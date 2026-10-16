package com.shiguang.interaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiguang.common.BizException;
import com.shiguang.common.counter.RecoverableCounterBuffer;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostStatus;
import com.shiguang.content.PostVisibility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 点赞服务：Redis Set 记录「谁赞了」，计数走 like:pending 缓冲，
 * 展示时用 数据库冗余计数 + pending 增量，定时任务把净增量落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    /** 计数缓冲 hash：field 为 p:{postId} / c:{commentId}，value 为净增量 */
    public static final String PENDING_KEY = "like:pending";

    private static final String POST_SET_PREFIX = "like:post:";
    private static final String POST_INIT_PREFIX = "like:init:post:";
    private static final String COMMENT_SET_PREFIX = "like:comment:";
    private static final String COMMENT_INIT_PREFIX = "like:init:comment:";
    private static final Duration INIT_MARKER_TTL = Duration.ofHours(1);

    private static final DefaultRedisScript<Long> UPDATE_MEMBERSHIP = new DefaultRedisScript<>("""
            if ARGV[2] == '1' then
                redis.call('SADD', KEYS[1], ARGV[1])
            else
                redis.call('SREM', KEYS[1], ARGV[1])
            end
            return redis.call('HINCRBY', KEYS[2], ARGV[3], ARGV[4])
            """, Long.class);

    private final Object[] mutationLocks = IntStream.range(0, 64).mapToObj(index -> new Object()).toArray();

    private final StringRedisTemplate redis;
    private final PostMapper postMapper;
    private final PostLikeMapper postLikeMapper;
    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RecoverableCounterBuffer counterBuffer;

    public LikeVO likePost(Long postId, Long userId) {
        synchronized (mutationLock("p:" + postId)) {
            Post post = requireAccessiblePost(postId, userId);
            ensurePostLikedInitialized(postId);
            boolean inserted = insertIgnore(postLikeMapper, new PostLike(postId, userId));
            updateMembership(postSetKey(postId), userId, true, "p:" + postId, inserted ? 1 : 0);
            if (inserted) {
                eventPublisher.publishEvent(new PostLikedEvent(postId, userId));
            }
            return postState(post, userId);
        }
    }

    public LikeVO unlikePost(Long postId, Long userId) {
        synchronized (mutationLock("p:" + postId)) {
            Post post = requireAccessiblePost(postId, userId);
            ensurePostLikedInitialized(postId);
            int deleted = postLikeMapper.delete(new LambdaQueryWrapper<PostLike>()
                    .eq(PostLike::getPostId, postId)
                    .eq(PostLike::getUserId, userId));
            updateMembership(postSetKey(postId), userId, false, "p:" + postId, -deleted);
            return postState(post, userId);
        }
    }

    public LikeVO likeComment(Long commentId, Long userId) {
        synchronized (mutationLock("c:" + commentId)) {
            Comment comment = requireComment(commentId);
            ensureCommentLikedInitialized(commentId);
            boolean inserted = insertIgnore(commentLikeMapper, new CommentLike(commentId, userId));
            updateMembership(commentSetKey(commentId), userId, true, "c:" + commentId, inserted ? 1 : 0);
            if (inserted) {
                eventPublisher.publishEvent(new CommentLikedEvent(commentId, userId));
            }
            return commentState(comment, userId);
        }
    }

    public LikeVO unlikeComment(Long commentId, Long userId) {
        synchronized (mutationLock("c:" + commentId)) {
            Comment comment = requireComment(commentId);
            ensureCommentLikedInitialized(commentId);
            int deleted = commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>()
                    .eq(CommentLike::getCommentId, commentId)
                    .eq(CommentLike::getUserId, userId));
            updateMembership(commentSetKey(commentId), userId, false, "c:" + commentId, -deleted);
            return commentState(comment, userId);
        }
    }

    /** 详情页：是否已点赞（未登录为 false） */
    public boolean isPostLiked(Long postId, Long userId) {
        if (userId == null) {
            return false;
        }
        ensurePostLikedInitialized(postId);
        return Boolean.TRUE.equals(redis.opsForSet().isMember(postSetKey(postId), userId.toString()));
    }

    /** 当前待落库的点赞净增量，包括尚未提交的 in-flight 批次 */
    public int postPendingDelta(Long postId) {
        return pendingDelta("p:" + postId);
    }

    public int commentPendingDelta(Long commentId) {
        return pendingDelta("c:" + commentId);
    }

    /** 批量取未提交增量，in-flight 批次按数据库收据排除 */
    public Map<Long, Integer> postPendingDeltas(Collection<Long> postIds) {
        return pendingDeltas(postIds, "p:");
    }

    public Map<Long, Integer> commentPendingDeltas(Collection<Long> commentIds) {
        return pendingDeltas(commentIds, "c:");
    }

    /** 批量查「当前用户是否赞过」（含集合重建，一次往返 + 少量补建） */
    public Map<Long, Boolean> postLikedMap(Collection<Long> postIds, Long userId) {
        if (userId == null || postIds.isEmpty()) {
            return Map.of();
        }
        refreshPostLikeSets(postIds);
        return likedMap(postIds, POST_SET_PREFIX, userId);
    }

    public Map<Long, Boolean> commentLikedMap(Collection<Long> commentIds, Long userId) {
        if (userId == null || commentIds.isEmpty()) {
            return Map.of();
        }
        refreshCommentLikeSets(commentIds);
        return likedMap(commentIds, COMMENT_SET_PREFIX, userId);
    }

    /** 作品删除后的清理 */
    public void cleanupPost(Long postId) {
        postLikeMapper.delete(new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, postId));
        redis.delete(List.of(postSetKey(postId), POST_INIT_PREFIX + postId));
        redis.opsForHash().delete(PENDING_KEY, "p:" + postId);
    }

    /** 评论删除后的清理 */
    public void cleanupComment(Long commentId) {
        commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>().eq(CommentLike::getCommentId, commentId));
        redis.delete(List.of(commentSetKey(commentId), COMMENT_INIT_PREFIX + commentId));
        redis.opsForHash().delete(PENDING_KEY, "c:" + commentId);
    }

    /** 恢复或暂存一批净增量，事务落库后确认（由定时任务调用） */
    public void flushPendingCounts() {
        counterBuffer.flush("like", PENDING_KEY, this::applyDelta);
    }

    public Post requirePublishedPost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(404, "作品不存在或已删除");
        }
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new BizException(1, "作品尚未发布，暂时无法互动");
        }
        return post;
    }

    /** 校验作品对该用户可访问：仅自己可见的作品只有作者本人能互动 */
    public Post requireAccessiblePost(Long postId, Long viewerId) {
        Post post = requirePublishedPost(postId);
        if (post.getVisibility() == PostVisibility.PRIVATE && !post.getUserId().equals(viewerId)) {
            throw new BizException(404, "作品不存在或已删除");
        }
        return post;
    }

    private Comment requireComment(Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(404, "评论不存在或已删除");
        }
        return comment;
    }

    private void applyDelta(String field, long delta) {
        if (field.startsWith("p:")) {
            Long postId = Long.parseLong(field.substring(2));
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("like_count = GREATEST(0, CAST(like_count AS SIGNED) + " + delta + ")"));
        } else if (field.startsWith("c:")) {
            Long commentId = Long.parseLong(field.substring(2));
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, commentId)
                    .setSql("like_count = GREATEST(0, CAST(like_count AS SIGNED) + " + delta + ")"));
        } else {
            throw new IllegalArgumentException("Invalid like counter field: " + field);
        }
    }

    private LikeVO postState(Post post, Long userId) {
        boolean liked = userId != null && Boolean.TRUE.equals(
                redis.opsForSet().isMember(postSetKey(post.getId()), userId.toString()));
        return LikeVO.builder()
                .liked(liked)
                .likeCount(Math.max(0, post.getLikeCount() + postPendingDelta(post.getId())))
                .build();
    }

    private LikeVO commentState(Comment comment, Long userId) {
        boolean liked = userId != null && Boolean.TRUE.equals(
                redis.opsForSet().isMember(commentSetKey(comment.getId()), userId.toString()));
        return LikeVO.builder()
                .liked(liked)
                .likeCount(Math.max(0, comment.getLikeCount() + commentPendingDelta(comment.getId())))
                .build();
    }

    private int pendingDelta(String field) {
        return Math.toIntExact(counterBuffer.pendingDeltas("like", PENDING_KEY, List.of(field))
                .getOrDefault(field, 0L));
    }

    private Map<Long, Integer> pendingDeltas(Collection<Long> ids, String prefix) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = ids.stream().distinct().toList();
        Map<String, Long> values = counterBuffer.pendingDeltas("like", PENDING_KEY,
                distinct.stream().map(id -> prefix + id).toList());
        Map<Long, Integer> result = new HashMap<>();
        for (Long identifier : distinct) {
            result.put(identifier, Math.toIntExact(values.getOrDefault(prefix + identifier, 0L)));
        }
        return result;
    }

    private Map<Long, Boolean> likedMap(Collection<Long> ids, String setKeyPrefix, Long userId) {
        List<Long> distinct = ids.stream().distinct().toList();
        List<Object> results = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (Long id : distinct) {
                connection.setCommands().sIsMember(keyBytes(setKeyPrefix + id), valueBytes(userId.toString()));
            }
            return null;
        });
        Map<Long, Boolean> result = new HashMap<>();
        for (int i = 0; i < distinct.size(); i++) {
            result.put(distinct.get(i), Boolean.TRUE.equals(results.get(i)));
        }
        return result;
    }

    private void updateMembership(String setKey, Long userId, boolean liked, String field, long delta) {
        redis.execute(UPDATE_MEMBERSHIP, List.of(setKey, PENDING_KEY), userId.toString(),
                liked ? "1" : "0", field, Long.toString(delta));
    }

    private Object mutationLock(String field) {
        return mutationLocks[Math.floorMod(field.hashCode(), mutationLocks.length)];
    }

    private <T> boolean insertIgnore(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, T entity) {
        try {
            return mapper.insert(entity) > 0;
        } catch (DuplicateKeyException e) {
            log.debug("重复点赞记录，忽略: {}", entity);
            return false;
        }
    }

    private void ensurePostLikedInitialized(Long postId) {
        ensureInitialized(postSetKey(postId), POST_INIT_PREFIX + postId,
                () -> postLikeMapper.selectList(new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, postId))
                        .stream().map(PostLike::getUserId).toList());
    }

    private void ensureCommentLikedInitialized(Long commentId) {
        ensureInitialized(commentSetKey(commentId), COMMENT_INIT_PREFIX + commentId,
                () -> commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLike>().eq(CommentLike::getCommentId, commentId))
                        .stream().map(CommentLike::getUserId).toList());
    }

    private void ensureInitialized(String setKey, String initKey, Supplier<List<Long>> loadUserIds) {
        if (Boolean.TRUE.equals(redis.hasKey(setKey)) || Boolean.TRUE.equals(redis.hasKey(initKey))) {
            return;
        }
        List<Long> userIds = loadUserIds.get();
        Boolean claimed = redis.opsForValue().setIfAbsent(initKey, "1", INIT_MARKER_TTL);
        if (Boolean.TRUE.equals(claimed)) {
            if (!userIds.isEmpty()) {
                redis.opsForSet().add(setKey, userIds.stream().map(String::valueOf).toArray(String[]::new));
            }
        }
    }

    private void refreshPostLikeSets(Collection<Long> postIds) {
        Set<Long> missing = findMissingInitialized(postIds, POST_SET_PREFIX, POST_INIT_PREFIX);
        if (missing.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> byPost = postLikeMapper.selectList(
                        new LambdaQueryWrapper<PostLike>().in(PostLike::getPostId, missing))
                .stream()
                .collect(Collectors.groupingBy(PostLike::getPostId,
                        Collectors.mapping(PostLike::getUserId, Collectors.toList())));
        rebuildSets(missing, byPost, POST_SET_PREFIX, POST_INIT_PREFIX);
    }

    private void refreshCommentLikeSets(Collection<Long> commentIds) {
        Set<Long> missing = findMissingInitialized(commentIds, COMMENT_SET_PREFIX, COMMENT_INIT_PREFIX);
        if (missing.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> byComment = commentLikeMapper.selectList(
                        new LambdaQueryWrapper<CommentLike>().in(CommentLike::getCommentId, missing))
                .stream()
                .collect(Collectors.groupingBy(CommentLike::getCommentId,
                        Collectors.mapping(CommentLike::getUserId, Collectors.toList())));
        rebuildSets(missing, byComment, COMMENT_SET_PREFIX, COMMENT_INIT_PREFIX);
    }

    private Set<Long> findMissingInitialized(Collection<Long> ids, String setPrefix, String initPrefix) {
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Set.of();
        }
        List<Object> results = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (Long id : distinct) {
                connection.keyCommands().exists(keyBytes(setPrefix + id));
                connection.keyCommands().exists(keyBytes(initPrefix + id));
            }
            return null;
        });
        Set<Long> missing = new HashSet<>();
        for (int i = 0; i < distinct.size(); i++) {
            boolean setExists = Boolean.TRUE.equals(results.get(i * 2));
            boolean initExists = Boolean.TRUE.equals(results.get(i * 2 + 1));
            if (!setExists && !initExists) {
                missing.add(distinct.get(i));
            }
        }
        return missing;
    }

    private void rebuildSets(Set<Long> missing, Map<Long, List<Long>> rowsByTarget, String setPrefix, String initPrefix) {
        redis.executePipelined((RedisCallback<Object>) connection -> {
            for (Long id : missing) {
                connection.stringCommands().set(keyBytes(initPrefix + id), valueBytes("1"));
                connection.keyCommands().pExpire(keyBytes(initPrefix + id), INIT_MARKER_TTL.toMillis());
                List<Long> userIds = rowsByTarget.getOrDefault(id, List.of());
                if (!userIds.isEmpty()) {
                    byte[] setKey = keyBytes(setPrefix + id);
                    for (Long userId : userIds) {
                        connection.setCommands().sAdd(setKey, valueBytes(userId.toString()));
                    }
                }
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private byte[] keyBytes(String key) {
        RedisSerializer<String> serializer = (RedisSerializer<String>) redis.getKeySerializer();
        return serializer.serialize(key);
    }

    @SuppressWarnings("unchecked")
    private byte[] valueBytes(String value) {
        RedisSerializer<String> serializer = (RedisSerializer<String>) redis.getValueSerializer();
        return serializer.serialize(value);
    }

    private static String postSetKey(Long postId) {
        return POST_SET_PREFIX + postId;
    }

    private static String commentSetKey(Long commentId) {
        return COMMENT_SET_PREFIX + commentId;
    }
}
