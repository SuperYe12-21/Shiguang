package com.shiguang.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiguang.common.BizException;
import com.shiguang.common.PageVO;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.interaction.Comment;
import com.shiguang.interaction.CommentCreatedEvent;
import com.shiguang.interaction.CommentLikedEvent;
import com.shiguang.interaction.CommentMapper;
import com.shiguang.interaction.PostLikedEvent;
import com.shiguang.storage.StorageService;
import com.shiguang.user.User;
import com.shiguang.user.UserFollowedEvent;
import com.shiguang.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通知中心：把点赞、评论、回复、关注聚合成可查看可跳转的通知。
 * 点赞按作品/评论折叠成一行并去重；其余类型每条独立。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_ACTORS = 100;
    private static final int DISPLAY_ACTORS = 3;
    private static final int CONTENT_SUMMARY_LENGTH = 100;
    private static final DateTimeFormatter CURSOR_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final NotificationMapper notificationMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    // ---------------- 事件入口 ----------------
    // 注意：这里必须用默认传播级别，事件监听器要能读到调用方事务里刚插入的评论/回复，
    // 改成 REQUIRES_NEW 会另开事务、读不到未提交的数据，导致评论类通知静默丢失。

    @EventListener
    @Transactional
    public void onPostLiked(PostLikedEvent event) {
        NotificationType type = NotificationType.LIKE_POST;
        guard(type, event.postId(), event.likerId(), () -> {
            Post post = postMapper.selectById(event.postId());
            if (post != null) {
                write(post.getUserId(), type, "post:" + event.postId(), event.likerId(),
                        event.postId(), null, null, null);
            }
        });
    }

    @EventListener
    @Transactional
    public void onCommentLiked(CommentLikedEvent event) {
        NotificationType type = NotificationType.LIKE_COMMENT;
        guard(type, event.commentId(), event.likerId(), () -> {
            Comment comment = commentMapper.selectById(event.commentId());
            if (comment != null) {
                write(comment.getUserId(), type, "comment:" + event.commentId(), event.likerId(),
                        comment.getPostId(), event.commentId(), comment.getRootId(), null);
            }
        });
    }

    @EventListener
    @Transactional
    public void onCommentCreated(CommentCreatedEvent event) {
        NotificationType type = event.rootId() == null
                ? NotificationType.COMMENT_POST : NotificationType.REPLY_COMMENT;
        guard(type, event.commentId(), event.authorUserId(), () -> {
            Comment comment = commentMapper.selectById(event.commentId());
            if (comment == null) {
                return;
            }
            String summary = summarize(comment.getContent());
            if (event.rootId() == null) {
                Post post = postMapper.selectById(event.postId());
                if (post != null) {
                    write(post.getUserId(), type, "cpost:" + event.commentId(), event.authorUserId(),
                            event.postId(), event.commentId(), null, summary);
                }
            } else {
                write(event.replyToUserId(), type, "reply:" + event.commentId(), event.authorUserId(),
                        event.postId(), event.commentId(), event.rootId(), summary);
            }
        });
    }

    @EventListener
    @Transactional
    public void onUserFollowed(UserFollowedEvent event) {
        NotificationType type = NotificationType.FOLLOW;
        guard(type, event.followerId(), event.followeeId(), () ->
                write(event.followeeId(), type, null, event.followerId(), null, null, null, null));
    }

    /**
     * 通知写入不能影响用户操作：与业务同事务，异常必须在这里吞掉，
     * 否则点赞/评论会跟着一起回滚。对外键不一致等脏数据同样只是跳过。
     */
    private void guard(NotificationType type, Object target, Long actorId, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("生成 {} 通知失败，target={} actor={}，已忽略: {}", type, target, actorId, e.toString());
        }
    }

    // ---------------- 写入 ----------------

    private void write(Long receiverId, NotificationType type, String mergeKey, Long actorId,
                       Long postId, Long commentId, Long rootId, String content) {
        if (receiverId == null || receiverId.equals(actorId)) {
            return;
        }
        if (mergeKey == null) {
            insertNew(receiverId, type, null, actorId, postId, commentId, rootId, content);
            return;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            Notification existing = notificationMapper.selectOne(new LambdaQueryWrapper<Notification>()
                    .eq(Notification::getUserId, receiverId)
                    .eq(Notification::getMergeKey, mergeKey)
                    .last("FOR UPDATE"));
            if (existing != null) {
                applyMerge(existing, actorId);
                return;
            }
            try {
                insertNew(receiverId, type, mergeKey, actorId, postId, commentId, rootId, content);
                return;
            } catch (DuplicateKeyException e) {
                // 并发下另一个事务刚插进去，下一轮走合并分支
                log.debug("通知并发插入冲突，转为合并: user={} mergeKey={}", receiverId, mergeKey);
            }
        }
    }

    private void insertNew(Long receiverId, NotificationType type, String mergeKey, Long actorId,
                           Long postId, Long commentId, Long rootId, String content) {
        LocalDateTime now = LocalDateTime.now();
        Notification notification = new Notification();
        notification.setUserId(receiverId);
        notification.setType(type);
        notification.setMergeKey(mergeKey);
        notification.setActorId(actorId);
        notification.setActorCount(1);
        notification.setActorIds(toActorIdsJson(List.of(actorId)));
        notification.setPostId(postId);
        notification.setCommentId(commentId);
        notification.setRootId(rootId);
        notification.setContent(content);
        notification.setCreatedAt(now);
        notification.setUpdatedAt(now);
        notificationMapper.insert(notification);
    }

    /** 合并：已在名单里就只把此人提到最前，不在才加人；两种情况都刷新时间并重新置为未读 */
    private void applyMerge(Notification existing, Long actorId) {
        List<Long> actorIds = parseActorIds(existing.getActorIds());
        boolean alreadyCounted = actorIds.contains(actorId);
        actorIds.remove(actorId);
        if (actorIds.size() < MAX_ACTORS) {
            actorIds.add(0, actorId);
        } else {
            alreadyCounted = true;
        }
        int count = existing.getActorCount() == null ? 1 : existing.getActorCount();
        if (!alreadyCounted) {
            count += 1;
        }
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, existing.getId())
                .set(Notification::getActorId, actorId)
                .set(Notification::getActorCount, count)
                .set(Notification::getActorIds, toActorIdsJson(actorIds))
                .set(Notification::getUpdatedAt, LocalDateTime.now())
                .set(Notification::getReadAt, null));
    }

    // ---------------- 查询 ----------------

    public UnreadCountVO unread(Long userId) {
        List<Map<String, Object>> rows = notificationMapper.selectMaps(new QueryWrapper<Notification>()
                .select("type", "COUNT(*) AS cnt")
                .eq("user_id", userId)
                .isNull("read_at")
                .groupBy("type"));
        long like = 0;
        long comment = 0;
        long follow = 0;
        for (Map<String, Object> row : rows) {
            Object typeRaw = pickIgnoreCase(row, "type");
            Long count = toLong(pickIgnoreCase(row, "cnt"));
            if (typeRaw == null || count == null) {
                continue;
            }
            String category = NotificationType.categoryOf(String.valueOf(typeRaw));
            if (NotificationType.Category.LIKE.equals(category)) {
                like += count;
            } else if (NotificationType.Category.COMMENT.equals(category)) {
                comment += count;
            } else if (NotificationType.Category.FOLLOW.equals(category)) {
                follow += count;
            }
        }
        return UnreadCountVO.builder()
                .total(like + comment + follow)
                .like(like)
                .comment(comment)
                .follow(follow)
                .build();
    }

    public PageVO<NotificationVO> list(Long userId, String category, String cursor, int limit) {
        List<NotificationType> types = typesOf(category);
        int size = normalizeLimit(limit);
        Cursor decoded = decodeCursor(cursor);

        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .in(Notification::getType, types)
                .orderByDesc(Notification::getUpdatedAt)
                .orderByDesc(Notification::getId)
                .last("LIMIT " + (size + 1));
        if (decoded != null) {
            // 折叠会刷新 updated_at 但不改 id，所以游标必须带上时间，否则翻页会漏数据或重复
            wrapper.and(w -> w
                    .lt(Notification::getUpdatedAt, decoded.updatedAt())
                    .or(o -> o.eq(Notification::getUpdatedAt, decoded.updatedAt())
                            .lt(Notification::getId, decoded.id())));
        }
        List<Notification> rows = notificationMapper.selectList(wrapper);
        boolean hasMore = rows.size() > size;
        List<Notification> page = hasMore ? rows.subList(0, size) : rows;

        String nextCursor = hasMore ? encodeCursor(page.get(page.size() - 1)) : null;
        return PageVO.<NotificationVO>builder()
                .items(toVOs(page))
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .build();
    }

    @Transactional
    public void markRead(Long userId, String category) {
        List<NotificationType> types = typesOf(category);
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .in(Notification::getType, types)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now())
                // 表上 updated_at 带 ON UPDATE CURRENT_TIMESTAMP，不显式写回原值的话
                // 「标记已读」会把行顶到列表最前面，时间也会变成「刚刚」
                .setSql("updated_at = updated_at"));
    }

    private List<NotificationType> typesOf(String category) {
        List<NotificationType> types = NotificationType.ofCategory(category);
        if (types.isEmpty()) {
            throw new BizException("未知的通知分类");
        }
        return types;
    }

    private List<NotificationVO> toVOs(List<Notification> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new LinkedHashSet<>();
        Set<Long> postIds = new HashSet<>();
        for (Notification row : rows) {
            userIds.add(row.getActorId());
            userIds.addAll(displayActorIds(row));
            if (row.getPostId() != null) {
                postIds.add(row.getPostId());
            }
        }
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Post> posts = postIds.isEmpty() ? Map.of()
                : postMapper.selectBatchIds(postIds).stream()
                        .collect(Collectors.toMap(Post::getId, Function.identity()));

        Map<Long, NotificationVO.Actor> actorCache = new HashMap<>();
        return rows.stream().map(row -> {
            List<NotificationVO.Actor> actors = displayActorIds(row).stream()
                    .map(id -> actorCache.computeIfAbsent(id, key -> toActor(users.get(key), key)))
                    .toList();
            Post post = row.getPostId() == null ? null : posts.get(row.getPostId());
            return NotificationVO.builder()
                    .id(row.getId())
                    .type(row.getType().name())
                    .category(row.getType().category())
                    .read(row.getReadAt() != null)
                    .actor(actors.isEmpty() ? null : actors.get(0))
                    .actorCount(row.getActorCount())
                    .actors(actors)
                    .postId(row.getPostId())
                    .commentId(row.getCommentId())
                    .rootId(row.getRootId())
                    .content(row.getContent())
                    .postCoverUrl(post == null ? null : thumbnailOf(post))
                    .updatedAt(row.getUpdatedAt())
                    .build();
        }).toList();
    }

    /** 折叠行展示的触发者：最近的排最前，最多 3 个 */
    private List<Long> displayActorIds(Notification row) {
        List<Long> ordered = new ArrayList<>();
        if (row.getActorId() != null) {
            ordered.add(row.getActorId());
        }
        for (Long id : parseActorIds(row.getActorIds())) {
            if (ordered.size() >= DISPLAY_ACTORS) {
                break;
            }
            if (!ordered.contains(id)) {
                ordered.add(id);
            }
        }
        return ordered;
    }

    private NotificationVO.Actor toActor(User user, Long fallbackId) {
        if (user == null) {
            return NotificationVO.Actor.builder().id(fallbackId).nickname("拾光用户").build();
        }
        return NotificationVO.Actor.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatarUrl(toAvatarUrl(user.getAvatarUrl()))
                .build();
    }

    private String summarize(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.length() <= CONTENT_SUMMARY_LENGTH
                ? trimmed : trimmed.substring(0, CONTENT_SUMMARY_LENGTH);
    }

    // ---------------- 工具 ----------------

    private record Cursor(LocalDateTime updatedAt, Long id) {
    }

    private static String encodeCursor(Notification notification) {
        return notification.getUpdatedAt().format(CURSOR_FORMAT) + "_" + notification.getId();
    }

    private static Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        int split = cursor.lastIndexOf('_');
        if (split <= 0 || split == cursor.length() - 1) {
            throw new BizException("分页游标格式不正确");
        }
        try {
            return new Cursor(
                    LocalDateTime.parse(cursor.substring(0, split), CURSOR_FORMAT),
                    Long.parseLong(cursor.substring(split + 1)));
        } catch (RuntimeException e) {
            throw new BizException("分页游标格式不正确");
        }
    }

    private List<Long> parseActorIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Long>>() {
            });
        } catch (Exception e) {
            log.warn("解析 actor_ids 失败，按空名单处理: {}", raw);
            return new ArrayList<>();
        }
    }

    private String toActorIdsJson(Collection<Long> actorIds) {
        try {
            return objectMapper.writeValueAsString(actorIds);
        } catch (Exception e) {
            log.warn("序列化 actor_ids 失败: {}", e.toString());
            return null;
        }
    }

    private String toAvatarUrl(String avatarUrl) {
        return toObjectUrl(avatarUrl);
    }

    /** 通知行右侧的缩略图：视频用封面，图文回退到第一张图 */
    private String thumbnailOf(Post post) {
        if (post.getCoverObject() != null && !post.getCoverObject().isBlank()) {
            return toObjectUrl(post.getCoverObject());
        }
        List<String> images = post.getImagesObject();
        if (images == null || images.isEmpty()) {
            return null;
        }
        return toObjectUrl(images.get(0));
    }

    private String toObjectUrl(String object) {
        if (object == null || object.isBlank()
                || object.startsWith("http://") || object.startsWith("https://")) {
            return object == null || object.isBlank() ? null : object;
        }
        return storageService.presignedGetUrl(object);
    }

    private static Object pickIgnoreCase(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}

