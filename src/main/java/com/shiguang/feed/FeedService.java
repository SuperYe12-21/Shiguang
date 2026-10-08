package com.shiguang.feed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.common.BizException;
import com.shiguang.common.PageVO;
import com.shiguang.common.SearchText;
import com.shiguang.content.Post;
import com.shiguang.content.PostDeletedEvent;
import com.shiguang.content.PostMapper;
import com.shiguang.content.PostPublishedEvent;
import com.shiguang.content.PostService;
import com.shiguang.content.PostStatus;
import com.shiguang.content.PostUpdatedEvent;
import com.shiguang.content.PostVO;
import com.shiguang.content.PostVisibility;
import com.shiguang.interaction.CommentCreatedEvent;
import com.shiguang.interaction.CommentDeletedEvent;
import com.shiguang.interaction.FavoriteService;
import com.shiguang.interaction.LikeService;
import com.shiguang.interaction.PostFavorite;
import com.shiguang.interaction.PostFavoriteMapper;
import com.shiguang.interaction.PostLike;
import com.shiguang.interaction.PostLikeMapper;
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
import java.util.Set;
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
    /** 朋友动态：互关好友数量上限（避免 IN 条件过长） */
    private static final int FRIEND_IDS_LIMIT = 200;
    private static final String FRIENDS_SEEN_KEY = "feed:friends:seen:";
    private static final String SEEN_CURSOR_PREFIX = "seen_";
    private static final String HISTORY_CURSOR_PREFIX = "hist_";

    private final PostMapper postMapper;
    private final PostService postService;
    private final LikeService likeService;
    private final FavoriteService favoriteService;
    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final FollowService followService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final FeedSeenService feedSeenService;
    private final ViewCountService viewCountService;

    /** 推荐流：登录用户优先未看过的内容，全部看完后按最久未看轮换；未登录保持时间倒序 */
    public PageVO<PostVO> feed(String cursor, int limit, Long viewerId) {
        int size = normalizeLimit(limit);
        if (viewerId == null) {
            return publicFeed(cursor, size);
        }
        return personalizedFeed(cursor, size, viewerId);
    }

    /** 未登录：时间倒序 + 首页短缓存 */
    private PageVO<PostVO> publicFeed(String cursor, int size) {
        boolean firstPage = cursor == null || cursor.isBlank();
        if (firstPage && size == DEFAULT_LIMIT) {
            CachedPage cached = readHomeCache();
            if (cached != null) {
                return buildPage(loadByIds(cached.ids()), cached.hasMore(), null);
            }
        }

        List<Post> rows = queryPosts(null, cursor, size + 1, PostStatus.PUBLISHED, PostVisibility.PUBLIC);
        boolean hasMore = rows.size() > size;
        List<Post> page = hasMore ? rows.subList(0, size) : rows;
        if (firstPage && size == DEFAULT_LIMIT) {
            writeHomeCache(page.stream().map(Post::getId).toList(), hasMore);
        }
        return buildPage(page, hasMore, null);
    }

    /** 已登录：第一段给未看过的，取尽后切换为 seen_ 游标进入轮换 */
    private PageVO<PostVO> personalizedFeed(String cursor, int size, Long viewerId) {
        if (cursor != null && cursor.startsWith(SEEN_CURSOR_PREFIX)) {
            return rotationPage(cursor, size, viewerId);
        }
        Set<Long> seen = feedSeenService.seenIds(viewerId);
        List<Post> collected = new ArrayList<>();
        String dbCursor = cursor == null || cursor.isBlank() ? null : cursor;
        boolean exhausted = false;
        int batch = Math.max(size * 3, size + 1);
        int guard = 0;
        while (collected.size() < size && !exhausted && guard++ < 20) {
            List<Post> rows = queryPosts(null, dbCursor, batch, PostStatus.PUBLISHED, PostVisibility.PUBLIC);
            if (rows.isEmpty()) {
                exhausted = true;
                break;
            }
            for (Post row : rows) {
                if (!seen.contains(row.getId())) {
                    collected.add(row);
                }
            }
            dbCursor = cursorOf(rows.get(rows.size() - 1));
            if (rows.size() < batch) {
                exhausted = true;
            }
        }
        if (collected.size() >= size) {
            return buildPage(collected.subList(0, size), true, viewerId);
        }
        if (exhausted) {
            if (collected.isEmpty()) {
                return rotationPage(SEEN_CURSOR_PREFIX + "0", size, viewerId);
            }
            return buildPage(collected, SEEN_CURSOR_PREFIX + "0", viewerId);
        }
        return buildPage(collected, true, viewerId);
    }

    /** 轮换段：按最久没看顺序（score 升序）循环输出已看过的作品 */
    private PageVO<PostVO> rotationPage(String cursor, int size, Long viewerId) {
        long total = feedSeenService.seenCount(viewerId);
        if (total <= 0) {
            return emptyPage();
        }
        long offset = parseSeenOffset(cursor);
        if (offset >= total) {
            offset = 0;
        }
        List<Post> page = new ArrayList<>();
        boolean wrapped = false;
        for (int attempt = 0; attempt < 6 && page.isEmpty(); attempt++) {
            List<Long> ids = feedSeenService.pageSeen(viewerId, offset, size);
            if (ids.isEmpty()) {
                break;
            }
            offset += ids.size();
            Map<Long, Post> byId = loadPublishedByIds(ids);
            for (Long id : ids) {
                Post post = byId.get(id);
                if (post != null) {
                    page.add(post);
                }
            }
            if (offset >= total) {
                if (page.isEmpty() && !wrapped) {
                    wrapped = true;
                    offset = 0;
                    continue;
                }
                break;
            }
        }
        if (page.isEmpty()) {
            return emptyPage();
        }
        long nextOffset = offset >= total ? 0 : offset;
        return buildPage(page, SEEN_CURSOR_PREFIX + nextOffset, viewerId);
    }

    /** 个人主页作品列表：自己看全部状态，他人只看已发布 */
    public PageVO<PostVO> userPosts(Long userId, Long viewerId, String cursor, int limit) {
        int size = normalizeLimit(limit);
        boolean own = userId.equals(viewerId);
        PostStatus status = own ? null : PostStatus.PUBLISHED;
        PostVisibility visibility = own ? null : PostVisibility.PUBLIC;
        List<Post> rows = queryPosts(userId, cursor, size + 1, status, visibility);
        boolean hasMore = rows.size() > size;
        List<Post> page = hasMore ? rows.subList(0, size) : rows;
        return buildPage(page, hasMore, viewerId);
    }

    /** 搜索公开作品：标题 / 简介模糊匹配，创建时间倒序游标分页 */
    public PageVO<PostVO> searchPosts(String keyword, String cursor, int limit, Long viewerId) {
        int size = normalizeLimit(limit);
        String kw = SearchText.normalize(keyword);
        if (kw.isEmpty()) {
            return PageVO.<PostVO>builder().items(List.of()).nextCursor(null).hasMore(false).build();
        }
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getStatus, PostStatus.PUBLISHED)
                .eq(Post::getVisibility, PostVisibility.PUBLIC)
                .and(w -> w.like(Post::getTitle, SearchText.escapeLike(kw))
                        .or().like(Post::getDescription, SearchText.escapeLike(kw)));
        if (cursor != null && !cursor.isBlank()) {
            Cursor c = parseCursor(cursor);
            wrapper.and(w -> w.lt(Post::getCreatedAt, c.time())
                    .or(o -> o.eq(Post::getCreatedAt, c.time()).lt(Post::getId, c.id())));
        }
        wrapper.orderByDesc(Post::getCreatedAt)
                .orderByDesc(Post::getId)
                .last("LIMIT " + (size + 1));
        List<Post> rows = postMapper.selectList(wrapper);
        boolean hasMore = rows.size() > size;
        List<Post> page = hasMore ? rows.subList(0, size) : rows;
        return buildPage(page, hasMore, viewerId);
    }

    /** 朋友动态：互关好友发布的公开作品，创建时间倒序游标分页（不含自己） */
    public PageVO<PostVO> friendsFeed(Long viewerId, String cursor, int limit) {
        int size = normalizeLimit(limit);
        List<Long> friendIds = new ArrayList<>(followService.friendIds(viewerId));
        if (friendIds.size() > FRIEND_IDS_LIMIT) {
            friendIds = friendIds.subList(0, FRIEND_IDS_LIMIT);
        }
        if (friendIds.isEmpty()) {
            return emptyPage();
        }
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, PostStatus.PUBLISHED)
                .eq(Post::getVisibility, PostVisibility.PUBLIC)
                .in(Post::getUserId, friendIds);
        if (cursor != null && !cursor.isBlank()) {
            Cursor c = parseCursor(cursor);
            wrapper.and(w -> w.lt(Post::getCreatedAt, c.time())
                    .or(o -> o.eq(Post::getCreatedAt, c.time()).lt(Post::getId, c.id())));
        }
        wrapper.orderByDesc(Post::getCreatedAt)
                .orderByDesc(Post::getId)
                .last("LIMIT " + (size + 1));
        List<Post> rows = postMapper.selectList(wrapper);
        boolean hasMore = rows.size() > size;
        List<Post> page = hasMore ? rows.subList(0, size) : rows;
        return buildPage(page, hasMore, viewerId);
    }

    /** 朋友动态红点状态：有比"上次查看"更新的好友作品则为未读 */
    public FriendsUnreadVO friendsUnread(Long userId) {
        Long latestId = friendsLatestPostId(userId);
        Long seenId = friendsSeenId(userId);
        boolean unread = latestId != null && (seenId == null || latestId > seenId);
        return new FriendsUnreadVO(unread, latestId, seenId);
    }

    /** 标记朋友动态已读：记录当前好友最新作品 id */
    public void markFriendsSeen(Long userId) {
        Long latestId = friendsLatestPostId(userId);
        if (latestId == null) {
            return;
        }
        try {
            redis.opsForValue().set(FRIENDS_SEEN_KEY + userId, String.valueOf(latestId));
        } catch (Exception e) {
            log.warn("标记朋友动态已读失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /** 互关好友（不含自己）的公开已发布作品中最新一条的 id */
    private Long friendsLatestPostId(Long viewerId) {
        List<Long> friendIds = new ArrayList<>(followService.friendIds(viewerId));
        if (friendIds.isEmpty()) {
            return null;
        }
        if (friendIds.size() > FRIEND_IDS_LIMIT) {
            friendIds = friendIds.subList(0, FRIEND_IDS_LIMIT);
        }
        List<Post> rows = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .select(Post::getId)
                .eq(Post::getStatus, PostStatus.PUBLISHED)
                .eq(Post::getVisibility, PostVisibility.PUBLIC)
                .in(Post::getUserId, friendIds)
                .orderByDesc(Post::getCreatedAt)
                .orderByDesc(Post::getId)
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0).getId();
    }

    private Long friendsSeenId(Long userId) {
        try {
            String raw = redis.opsForValue().get(FRIENDS_SEEN_KEY + userId);
            return raw == null ? null : Long.parseLong(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /** 观看历史：按最近观看时间倒序分页（仅自己可见，已删除/不可见的过滤掉） */
    public PageVO<PostVO> history(Long viewerId, String cursor, int limit) {
        if (viewerId == null) {
            return emptyPage();
        }
        int size = normalizeLimit(limit);
        long offset = parseHistoryOffset(cursor);
        List<Long> ids = feedSeenService.pageSeenDesc(viewerId, offset, size);
        if (ids.isEmpty()) {
            return emptyPage();
        }
        Map<Long, Post> posts = loadPublishedByIds(ids);
        List<Post> ordered = ids.stream().map(posts::get).filter(Objects::nonNull).toList();
        boolean maybeMore = ids.size() == size;
        String nextCursor = maybeMore ? HISTORY_CURSOR_PREFIX + (offset + ids.size()) : null;
        return buildPage(ordered, nextCursor, viewerId);
    }

    /** 清空观看历史（不影响已累计的播放量） */
    public void clearHistory(Long viewerId) {
        feedSeenService.clear(viewerId);
    }

    /** 用户点赞过的作品列表（仅已发布），按点赞时间倒序 */
    public PageVO<PostVO> userLikes(Long userId, Long viewerId, String cursor, int limit) {
        int size = normalizeLimit(limit);
        LambdaQueryWrapper<PostLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostLike::getUserId, userId);
        if (cursor != null && !cursor.isBlank()) {
            Cursor c = parseCursor(cursor);
            wrapper.and(w -> w.lt(PostLike::getCreatedAt, c.time())
                    .or(o -> o.eq(PostLike::getCreatedAt, c.time()).lt(PostLike::getId, c.id())));
        }
        wrapper.orderByDesc(PostLike::getCreatedAt)
                .orderByDesc(PostLike::getId)
                .last("LIMIT " + (size + 1));
        List<PostLike> likes = postLikeMapper.selectList(wrapper);
        boolean hasMore = likes.size() > size;
        List<PostLike> pageLikes = hasMore ? likes.subList(0, size) : likes;
        List<Post> posts = new ArrayList<>();
        for (PostLike like : pageLikes) {
            Post post = postMapper.selectById(like.getPostId());
            if (post != null && PostStatus.PUBLISHED.equals(post.getStatus())
                    && (!PostVisibility.PRIVATE.equals(post.getVisibility())
                            || post.getUserId().equals(viewerId))) {
                posts.add(post);
            }
        }
        if (posts.isEmpty()) {
            return PageVO.<PostVO>builder().items(List.of())
                    .hasMore(hasMore)
                    .nextCursor(hasMore && !pageLikes.isEmpty()
                            ? likeCursorOf(pageLikes.get(pageLikes.size() - 1)) : null)
                    .build();
        }
        PageVO<PostVO> result = buildPage(posts, hasMore, viewerId);
        if (hasMore) {
            result.setNextCursor(likeCursorOf(pageLikes.get(pageLikes.size() - 1)));
        }
        return result;
    }

    private static String likeCursorOf(PostLike like) {
        long epoch = like.getCreatedAt() == null ? 0
                : like.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        return epoch + "_" + like.getId();
    }

    /** 用户收藏过的作品列表（仅已发布），按收藏时间倒序 */
    public PageVO<PostVO> userFavorites(Long userId, Long viewerId, String cursor, int limit) {
        int size = normalizeLimit(limit);
        LambdaQueryWrapper<PostFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostFavorite::getUserId, userId);
        if (cursor != null && !cursor.isBlank()) {
            Cursor c = parseCursor(cursor);
            wrapper.and(w -> w.lt(PostFavorite::getCreatedAt, c.time())
                    .or(o -> o.eq(PostFavorite::getCreatedAt, c.time()).lt(PostFavorite::getId, c.id())));
        }
        wrapper.orderByDesc(PostFavorite::getCreatedAt)
                .orderByDesc(PostFavorite::getId)
                .last("LIMIT " + (size + 1));
        List<PostFavorite> favorites = postFavoriteMapper.selectList(wrapper);
        boolean hasMore = favorites.size() > size;
        List<PostFavorite> pageFavorites = hasMore ? favorites.subList(0, size) : favorites;
        List<Post> posts = new ArrayList<>();
        for (PostFavorite favorite : pageFavorites) {
            Post post = postMapper.selectById(favorite.getPostId());
            if (post != null && PostStatus.PUBLISHED.equals(post.getStatus())
                    && (!PostVisibility.PRIVATE.equals(post.getVisibility())
                            || post.getUserId().equals(viewerId))) {
                posts.add(post);
            }
        }
        if (posts.isEmpty()) {
            return PageVO.<PostVO>builder().items(List.of())
                    .hasMore(hasMore)
                    .nextCursor(hasMore && !pageFavorites.isEmpty()
                            ? favoriteCursorOf(pageFavorites.get(pageFavorites.size() - 1)) : null)
                    .build();
        }
        PageVO<PostVO> result = buildPage(posts, hasMore, viewerId);
        if (hasMore) {
            result.setNextCursor(favoriteCursorOf(pageFavorites.get(pageFavorites.size() - 1)));
        }
        return result;
    }

    private static String favoriteCursorOf(PostFavorite favorite) {
        long epoch = favorite.getCreatedAt() == null ? 0
                : favorite.getCreatedAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        return epoch + "_" + favorite.getId();
    }

    private List<Post> queryPosts(Long userId, String cursorStr, int limit, PostStatus status,
                                  PostVisibility visibility) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Post::getUserId, userId);
        }
        if (status != null) {
            wrapper.eq(Post::getStatus, status);
        }
        if (visibility != null) {
            wrapper.eq(Post::getVisibility, visibility);
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
        String nextCursor = hasMore && !page.isEmpty() ? cursorOf(page.get(page.size() - 1)) : null;
        return buildPage(page, nextCursor, viewerId);
    }

    private PageVO<PostVO> buildPage(List<Post> page, String nextCursor, Long viewerId) {
        List<PostVO> items = new ArrayList<>();
        if (!page.isEmpty()) {
            List<Long> ids = page.stream().map(Post::getId).toList();
            Map<Long, Integer> pending = likeService.postPendingDeltas(ids);
            Map<Long, Long> viewPending = viewCountService.pendingDeltas(ids);
            Map<Long, Boolean> liked = likeService.postLikedMap(ids, viewerId);
            Map<Long, Boolean> favorited = favoriteService.favoritedMap(ids, viewerId);
            Map<Long, Long> favoriteCounts = favoriteService.countMap(ids);
            List<Long> authorIds = page.stream().map(Post::getUserId).distinct().toList();
            Map<Long, Boolean> following = followService.followingMap(viewerId, authorIds);
            for (Post post : page) {
                PostVO vo = postService.toVO(post);
                vo.setLikeCount(Math.max(0, vo.getLikeCount() + pending.getOrDefault(post.getId(), 0)));
                vo.setViewCount(Math.max(0, vo.getViewCount() + viewPending.getOrDefault(post.getId(), 0L)));
                vo.setLiked(viewerId != null && liked.getOrDefault(post.getId(), false));
                vo.setFavorited(viewerId != null && favorited.getOrDefault(post.getId(), false));
                vo.setFavoriteCount(favoriteCounts.getOrDefault(post.getId(), 0L));
                if (vo.getAuthor() != null && viewerId != null) {
                    vo.getAuthor().setFollowing(following.getOrDefault(post.getUserId(), false));
                }
                items.add(vo);
            }
        }
        return PageVO.<PostVO>builder().items(items).nextCursor(nextCursor).hasMore(nextCursor != null).build();
    }

    private List<Post> loadByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> byId = postMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private Map<Long, Post> loadPublishedByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return postMapper.selectBatchIds(ids).stream()
                .filter(post -> PostStatus.PUBLISHED.equals(post.getStatus())
                        && !PostVisibility.PRIVATE.equals(post.getVisibility()))
                .collect(Collectors.toMap(Post::getId, Function.identity(), (a, b) -> a));
    }

    private static long parseSeenOffset(String cursor) {
        try {
            return Long.parseLong(cursor.substring(SEEN_CURSOR_PREFIX.length()));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static long parseHistoryOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(cursor.substring(HISTORY_CURSOR_PREFIX.length()));
        } catch (Exception e) {
            return 0L;
        }
    }

    private static PageVO<PostVO> emptyPage() {
        return PageVO.<PostVO>builder().items(List.of()).nextCursor(null).hasMore(false).build();
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
    public void onPostUpdated(PostUpdatedEvent event) {
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
