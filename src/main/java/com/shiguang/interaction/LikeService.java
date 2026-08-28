package com.shiguang.interaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiguang.common.BizException;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    private static final DefaultRedisScript<List<Object>> POP_PENDING_SCRIPT = new DefaultRedisScript<>(
            "local f = redis.call('HGETALL', KEYS[1]); "
                    + "if #f == 0 then return {} end; "
                    + "redis.call('DEL', KEYS[1]); "
                    + "return f",
            (Class<List<Object>>) (Class<?>) List.class);

    private final StringRedisTemplate redis;
    private final PostMapper postMapper;
    private final PostLikeMapper postLikeMapper;
    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;

    public LikeVO likePost(Long postId, Long userId) {
        Post post = requirePublishedPost(postId);
        ensurePostLikedInitialized(postId);
        if (addMember(postSetKey(postId), userId)) {
            incrPending("p:" + postId, 1);
            insertIgnore(postLikeMapper, new PostLike(postId, userId));
        }
        return postState(post, userId);
    }

    public LikeVO unlikePost(Long postId, Long userId) {
        Post post = requirePublishedPost(postId);
        ensurePostLikedInitialized(postId);
        if (removeMember(postSetKey(postId), userId)) {
            incrPending("p:" + postId, -1);
            postLikeMapper.delete(new LambdaQueryWrapper<PostLike>()
                    .eq(PostLike::getPostId, postId)
                    .eq(PostLike::getUserId, userId));
        }
        return postState(post, userId);
    }

    public LikeVO likeComment(Long commentId, Long userId) {
        Comment comment = requireComment(commentId);
        ensureCommentLikedInitialized(commentId);
        if (addMember(commentSetKey(commentId), userId)) {
            incrPending("c:" + commentId, 1);
            insertIgnore(commentLikeMapper, new CommentLike(commentId, userId));
        }
        return commentState(comment, userId);
    }

    public LikeVO unlikeComment(Long commentId, Long userId) {
        Comment comment = requireComment(commentId);
        ensureCommentLikedInitialized(commentId);
        if (removeMember(commentSetKey(commentId), userId)) {
            incrPending("c:" + commentId, -1);
            commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>()
                    .eq(CommentLike::getCommentId, commentId)
                    .eq(CommentLike::getUserId, userId));
        }
        return commentState(comment, userId);
    }

    /** 详情页：是否已点赞（未登录为 false） */
    public boolean isPostLiked(Long postId, Long userId) {
        if (userId == null) {
            return false;
        }
        ensurePostLikedInitialized(postId);
        return Boolean.TRUE.equals(redis.opsForSet().isMember(postSetKey(postId), userId.toString()));
    }

    /** 当前待落库的点赞净增量 */
    public int postPendingDelta(Long postId) {
        return pendingDelta("p:" + postId);
    }

    public int commentPendingDelta(Long commentId) {
        return pendingDelta("c:" + commentId);
    }

    /** 批量取 pending 增量（一次 Redis 往返） */
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

    /** 把 pending 净增量原子地搬到数据库（由定时任务调用） */
    public void flushPendingCounts() {
        List<Object> popped = redis.execute(POP_PENDING_SCRIPT, List.of(PENDING_KEY));
        if (popped == null || popped.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < popped.size(); i += 2) {
            String field = (String) popped.get(i);
            long delta;
            try {
                delta = Long.parseLong((String) popped.get(i + 1));
            } catch (Exception e) {
                log.warn("like pending 增量非法: field={} value={}", field, popped.get(i + 1));
                continue;
            }
            try {
                applyDelta(field, delta);
            } catch (Exception e) {
                log.error("点赞计数落库失败，回滚 pending: field={}", field, e);
                redis.opsForHash().increment(PENDING_KEY, field, delta);
            }
        }
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
                    .setSql("like_count = GREATEST(0, like_count + " + delta + ")"));
        } else if (field.startsWith("c:")) {
            Long commentId = Long.parseLong(field.substring(2));
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                    .eq(Comment::getId, commentId)
                    .setSql("like_count = GREATEST(0, like_count + " + delta + ")"));
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
        Object value = redis.opsForHash().get(PENDING_KEY, field);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Map<Long, Integer> pendingDeltas(Collection<Long> ids, String prefix) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = ids.stream().distinct().toList();
        List<String> values = redis.<String, String>opsForHash().multiGet(PENDING_KEY,
                distinct.stream().map(id -> prefix + id).toList());
        Map<Long, Integer> result = new HashMap<>();
        for (int i = 0; i < distinct.size(); i++) {
            Object value = values.get(i);
            if (value != null) {
                try {
                    result.put(distinct.get(i), Integer.parseInt(value.toString()));
                } catch (NumberFormatException ignored) {
                    // 非法值按 0 处理
                }
            }
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

    private boolean addMember(String setKey, Long userId) {
        Long added = redis.opsForSet().add(setKey, userId.toString());
        return added != null && added > 0;
    }

    private boolean removeMember(String setKey, Long userId) {
        Long removed = redis.opsForSet().remove(setKey, userId.toString());
        return removed != null && removed > 0;
    }

    private void incrPending(String field, long delta) {
        redis.opsForHash().increment(PENDING_KEY, field, delta);
    }

    private <T> void insertIgnore(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, T entity) {
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException e) {
            log.debug("重复点赞记录，忽略: {}", entity);
        }
    }

    private void ensurePostLikedInitialized(Long postId) {
        ensureInitialized(postSetKey(postId), POST_INIT_PREFIX + postId,
                postLikeMapper.selectList(new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, postId))
                        .stream().map(PostLike::getUserId).toList());
    }

    private void ensureCommentLikedInitialized(Long commentId) {
        ensureInitialized(commentSetKey(commentId), COMMENT_INIT_PREFIX + commentId,
                commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLike>().eq(CommentLike::getCommentId, commentId))
                        .stream().map(CommentLike::getUserId).toList());
    }

    private void ensureInitialized(String setKey, String initKey, List<Long> userIds) {
        if (Boolean.TRUE.equals(redis.hasKey(setKey)) || Boolean.TRUE.equals(redis.hasKey(initKey))) {
            return;
        }
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
