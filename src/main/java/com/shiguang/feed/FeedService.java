package com.shiguang.feed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.common.BizException;
import com.shiguang.common.PageVO;
import com.shiguang.content.Post;
import com.shiguang.content.PostDeletedEvent;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostPublishedEvent;
import com.shiguang.content.PostService;
import com.shiguang.content.PostStatus;
import com.shiguang.content.PostVO;
import com.shiguang.interaction.CommentCreatedEvent;
import com.shiguang.interaction.CommentDeletedEvent;
import com.shiguang.interaction.LikeService;
import com.shiguang.user.FollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private static final String HOME_CACHE_KEY = "feed:home:ids";
    private static final Duration HOME_CACHE_TTL = Duration.ofSeconds(30);
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;

    private final PostMapper postMapper;
    private final PostService postService;
    private final LikeService likeService;
    private final FollowService followService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 推荐流：时间倒序游标分页，首页走 Redis 缓存 */
    public PageVO<PostVO> feed(String cursor, int limit, Long viewerId) {
        int size = normalizeLimit(limit);
        boolean firstPage = cursor == null || cursor.isBlank();
        if (firstPage && size == DEFAULT_LIMIT) {
            CachedPage cached = readHomeCache();
            if (cached != null) {
                return buildPage(loadByIds(cached.ids()), cached.hasMore(), viewerId);
            }
        }

        List<Post> rows = queryPosts(null, cursor, size + 1, PostStatus.PUBLISHED);
        boolean hasMore = rows.size() > size;
        List<Post> page = hasMore ? rows.subList(0, size) : rows;
        if (firstPage && size == DEFAULT_LIMIT) {
            writeHomeCache(page.stream().map(Post::getId).toList(), hasMore);
        }
        return buildPage(page, hasMore, viewerId);
    }

    /** 个人主页作品列表：自己看全部状态，他人只看已发布 */
    public PageVO<PostVO> userPosts(Long userId, Long viewerId, String cursor, int limit) {
        int size = normalizeLimit(limit);
        boolean own = userId.equals(viewerId);
        PostStatus status = own ? null : PostStatus.PUBLISHED;
        List<Post> rows = queryPosts(userId, cursor, size + 1, status);
        boolean hasMore = rows.size() > size;
        List<Post> page = hasMore ? rows.subList(0, size) : rows;
        return buildPage(page, hasMore, viewerId);
    }

    private List<Post> queryPosts(Long userId, String cursorStr, int limit, PostStatus status) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Post::getUserId, userId);
        }
        if (status != null) {
            wrapper.eq(Post::getStatus, status);
        }
        if (cursorStr != null && !cursorStr.isBlank()) {
            Cursor cursor = parseCursor(cursorStr);
            wrapper.and(w -> w.lt(Post::getCreatedAt, cursor.time())
                    .or(o -> o.eq(Post::getCreatedAt, cursor.time()).lt(Post::getId, cursor.id())));
        }
        wrapper.orderByDesc(Post::getCreatedAt)
                .orderByDesc(Post::getId)
                .last("LIMIT " + limit);
        return postMapper.selectList(wrapper);
    }

    private PageVO<PostVO> buildPage(List<Post> page, boolean hasMore, Long viewerId) {
        List<PostVO> items = new ArrayList<>();
        if (!page.isEmpty()) {
            List<Long> ids = page.stream().map(Post::getId).toList();
            Map<Long, Integer> pending = likeService.postPendingDeltas(ids);
            Map<Long, Boolean> liked = likeService.postLikedMap(ids, viewerId);
            List<Long> authorIds = page.stream().map(Post::getUserId).distinct().toList();
            Map<Long, Boolean> following = followService.followingMap(viewerId, authorIds);
            for (Post post : page) {
                PostVO vo = postService.toVO(post);
                vo.setLikeCount(Math.max(0, vo.getLikeCount() + pending.getOrDefault(post.getId(), 0)));
                vo.setLiked(viewerId != null && liked.getOrDefault(post.getId(), false));
                if (vo.getAuthor() != null && viewerId != null) {
                    vo.getAuthor().setFollowing(following.getOrDefault(post.getUserId(), false));
                }
                items.add(vo);
            }
        }
        String nextCursor = hasMore ? cursorOf(page.get(page.size() - 1)) : null;
        return PageVO.<PostVO>builder().items(items).nextCursor(nextCursor).hasMore(hasMore).build();
    }

    private List<Post> loadByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> byId = postMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @EventListener
    public void onPostPublished(PostPublishedEvent event) {
        evictHome();
    }

    @EventListener
    public void onPostDeleted(PostDeletedEvent event) {
        evictHome();
    }

    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        evictHome();
    }

    @EventListener
    public void onCommentDeleted(CommentDeletedEvent event) {
        evictHome();
    }

    public void evictHome() {
        redis.delete(HOME_CACHE_KEY);
    }

    private CachedPage readHomeCache() {
        String json = redis.opsForValue().get(HOME_CACHE_KEY);
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            List<Long> ids = new ArrayList<>();
            node.path("ids").forEach(n -> ids.add(n.asLong()));
            return new CachedPage(ids, node.path("hasMore").asBoolean(false));
        } catch (Exception e) {
            log.warn("首页信息流缓存解析失败，忽略: {}", e.getMessage());
            return null;
        }
    }

    private void writeHomeCache(List<Long> ids, boolean hasMore) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("ids", ids, "hasMore", hasMore));
            redis.opsForValue().set(HOME_CACHE_KEY, json, HOME_CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("首页信息流缓存写入失败: {}", e.getMessage());
        }
    }

    private Cursor parseCursor(String cursor) {
        try {
            String[] parts = cursor.split("_");
            if (parts.length != 2) {
                throw new IllegalArgumentException();
            }
            long epoch = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            return new Cursor(LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault()), id);
        } catch (Exception e) {
            throw new BizException(1, "分页游标无效");
        }
    }

    private static String cursorOf(Post post) {
        long epoch = post.getCreatedAt() == null ? 0
                : post.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        return epoch + "_" + post.getId();
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private record Cursor(LocalDateTime time, Long id) {
    }

    private record CachedPage(List<Long> ids, boolean hasMore) {
    }
}