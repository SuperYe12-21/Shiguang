package com.shiguang.interaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiguang.common.BizException;
import com.shiguang.common.PageVO;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.storage.StorageService;
import com.shiguang.user.User;
import com.shiguang.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论服务：两级盖楼。
 * 顶层评论 root_id 为 NULL，回复的 root_id 指向所属顶层评论，永不出现第三层。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final LikeService likeService;
    private final ApplicationEventPublisher eventPublisher;
    private final StorageService storageService;

    /** 顶层评论分页，时间倒序（最新在前） */
    public PageVO<CommentVO> list(Long postId, Long cursorId, int limit, Long viewerId) {
        Post post = likeService.requireAccessiblePost(postId, viewerId);
        int size = normalizeLimit(limit);
        List<Comment> rows = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .isNull(Comment::getRootId)
                .lt(cursorId != null && cursorId > 0, Comment::getId, cursorId)
                .orderByDesc(Comment::getId)
                .last("LIMIT " + (size + 1)));
        boolean hasMore = rows.size() > size;
        List<Comment> page = hasMore ? rows.subList(0, size) : rows;

        List<CommentVO> items = toVOs(page, viewerId, post.getUserId(), replyCountsOf(page));
        String nextCursor = hasMore ? page.get(page.size() - 1).getId().toString() : null;
        return PageVO.<CommentVO>builder().items(items).nextCursor(nextCursor).hasMore(hasMore).build();
    }

    /** 某个楼层的回复分页，时间正序；传入顶层评论 id 或任意回复 id 都能定位到楼层 */
    public PageVO<CommentVO> listReplies(Long commentId, Long cursorId, int limit, Long viewerId) {
        Comment anchor = requireComment(commentId);
        Post post = likeService.requireAccessiblePost(anchor.getPostId(), viewerId);
        Long rootId = anchor.getRootId() != null ? anchor.getRootId() : anchor.getId();
        int size = normalizeLimit(limit);
        List<Comment> rows = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getRootId, rootId)
                .gt(cursorId != null && cursorId > 0, Comment::getId, cursorId)
                .orderByAsc(Comment::getId)
                .last("LIMIT " + (size + 1)));
        boolean hasMore = rows.size() > size;
        List<Comment> page = hasMore ? rows.subList(0, size) : rows;

        List<CommentVO> items = toVOs(page, viewerId, post.getUserId(), Map.of());
        String nextCursor = hasMore ? page.get(page.size() - 1).getId().toString() : null;
        return PageVO.<CommentVO>builder().items(items).nextCursor(nextCursor).hasMore(hasMore).build();
    }

    @Transactional
    public CommentVO create(Long postId, Long userId, String content) {
        Post post = likeService.requireAccessiblePost(postId, userId);
        String trimmed = requireContent(content, "评论内容不能为空");
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(trimmed);
        comment.setLikeCount(0);
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, postId)
                .setSql("comment_count = comment_count + 1"));

        eventPublisher.publishEvent(new CommentCreatedEvent(postId, comment.getId(), userId, null, null, null));
        User author = userMapper.selectById(userId);
        return toVO(comment, userId, post.getUserId(), false, 0, author, null, null);
    }

    /** 回复评论：被回复的对象可以是顶层评论，也可以是某条回复；结果始终挂在同一个楼层下 */
    @Transactional
    public CommentVO createReply(Long commentId, Long userId, String content) {
        Comment parent = requireComment(commentId);
        Post post = likeService.requireAccessiblePost(parent.getPostId(), userId);
        String trimmed = requireContent(content, "回复内容不能为空");

        Comment reply = new Comment();
        reply.setPostId(parent.getPostId());
        reply.setParentId(parent.getId());
        reply.setRootId(parent.getRootId() != null ? parent.getRootId() : parent.getId());
        reply.setReplyToUserId(parent.getUserId());
        reply.setUserId(userId);
        reply.setContent(trimmed);
        reply.setLikeCount(0);
        reply.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(reply);

        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, parent.getPostId())
                .setSql("comment_count = comment_count + 1"));

        eventPublisher.publishEvent(new CommentCreatedEvent(parent.getPostId(), reply.getId(), userId,
                reply.getParentId(), reply.getRootId(), reply.getReplyToUserId()));
        User author = userMapper.selectById(userId);
        User replyTo = userMapper.selectById(reply.getReplyToUserId());
        return toVO(reply, userId, post.getUserId(), false, 0, author, replyTo, null);
    }

    /** 评论作者本人或作品作者可删；删顶层评论时整层（含回复）一起删 */
    @Transactional
    public void delete(Long commentId, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(404, "评论不存在或已删除");
        }
        Post post = postMapper.selectById(comment.getPostId());
        boolean isCommentAuthor = comment.getUserId().equals(userId);
        boolean isPostAuthor = post != null && post.getUserId().equals(userId);
        if (!isCommentAuthor && !isPostAuthor) {
            throw new BizException(403, "无权删除该评论");
        }

        int removed;
        if (comment.getRootId() == null) {
            List<Comment> replies = commentMapper.selectList(
                    new LambdaQueryWrapper<Comment>().eq(Comment::getRootId, commentId));
            for (Comment reply : replies) {
                likeService.cleanupComment(reply.getId());
            }
            if (!replies.isEmpty()) {
                commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getRootId, commentId));
            }
            likeService.cleanupComment(commentId);
            commentMapper.deleteById(commentId);
            removed = 1 + replies.size();
        } else {
            likeService.cleanupComment(commentId);
            commentMapper.deleteById(commentId);
            removed = 1;
        }

        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, comment.getPostId())
                .setSql("comment_count = GREATEST(0, comment_count - " + removed + ")"));
        eventPublisher.publishEvent(new CommentDeletedEvent(comment.getPostId()));
    }

    /** 作品删除时级联清理其全部评论与点赞状态 */
    @Transactional
    public void cleanupPost(Long postId) {
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, postId));
        for (Comment comment : comments) {
            likeService.cleanupComment(comment.getId());
        }
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, postId));
    }

    /** 一页顶层评论的回复数：一次 GROUP BY 拿到，不维护冗余计数，永远不会漂移 */
    private Map<Long, Integer> replyCountsOf(List<Comment> tops) {
        if (tops.isEmpty()) {
            return Map.of();
        }
        List<Long> rootIds = tops.stream().map(Comment::getId).toList();
        List<Map<String, Object>> rows = commentMapper.selectMaps(new QueryWrapper<Comment>()
                .select("root_id", "COUNT(*) AS cnt")
                .in("root_id", rootIds)
                .groupBy("root_id"));
        Map<Long, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long rootId = toLong(pickIgnoreCase(row, "root_id"));
            Long count = toLong(pickIgnoreCase(row, "cnt"));
            if (rootId != null && count != null) {
                counts.put(rootId, count.intValue());
            }
        }
        return counts;
    }

    private List<CommentVO> toVOs(List<Comment> comments, Long viewerId, Long postAuthorId,
                                  Map<Long, Integer> replyCounts) {
        if (comments.isEmpty()) {
            return List.of();
        }
        List<Long> ids = comments.stream().map(Comment::getId).toList();
        Set<Long> userIds = new LinkedHashSet<>();
        for (Comment comment : comments) {
            userIds.add(comment.getUserId());
            if (comment.getReplyToUserId() != null) {
                userIds.add(comment.getReplyToUserId());
            }
        }
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Boolean> liked = likeService.commentLikedMap(ids, viewerId);
        Map<Long, Integer> pending = likeService.commentPendingDeltas(ids);

        return comments.stream()
                .map(comment -> toVO(comment, viewerId, postAuthorId,
                        liked.getOrDefault(comment.getId(), false),
                        pending.getOrDefault(comment.getId(), 0),
                        users.get(comment.getUserId()),
                        comment.getReplyToUserId() == null ? null : users.get(comment.getReplyToUserId()),
                        replyCounts.get(comment.getId())))
                .toList();
    }

    private CommentVO toVO(Comment comment, Long viewerId, Long postAuthorId, boolean liked,
                           int pendingDelta, User author, User replyToUser, Integer replyCount) {
        CommentVO.Author authorVO = author == null ? null
                : CommentVO.Author.builder()
                        .id(author.getId())
                        .nickname(author.getNickname())
                        .avatarUrl(toAvatarUrl(author.getAvatarUrl()))
                        .build();
        // 只有「回复的是一条回复」才展示 @前缀，回复顶层评论不展示
        boolean showReplyTo = replyToUser != null
                && comment.getParentId() != null
                && !comment.getParentId().equals(comment.getRootId());
        CommentVO.ReplyToUser replyToVO = showReplyTo
                ? CommentVO.ReplyToUser.builder()
                        .id(replyToUser.getId())
                        .nickname(replyToUser.getNickname())
                        .build()
                : null;
        // 顶层评论回复数缺省为 0，回复自身不带该字段
        Integer replyCountOfComment = comment.getRootId() == null
                ? (replyCount == null ? 0 : replyCount)
                : null;
        return CommentVO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .parentId(comment.getParentId())
                .rootId(comment.getRootId())
                .replyToUser(replyToVO)
                .replyCount(replyCountOfComment)
                .canDelete(viewerId != null
                        && (viewerId.equals(comment.getUserId()) || viewerId.equals(postAuthorId)))
                .userId(comment.getUserId())
                .content(comment.getContent())
                .likeCount(Math.max(0, comment.getLikeCount() + pendingDelta))
                .liked(liked)
                .mine(viewerId != null && viewerId.equals(comment.getUserId()))
                .author(authorVO)
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private Comment requireComment(Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(404, "评论不存在或已删除");
        }
        return comment;
    }

    private static String requireContent(String content, String message) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new BizException(message);
        }
        return trimmed;
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

    private String toAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()
                || avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            return avatarUrl;
        }
        return storageService.presignedGetUrl(avatarUrl);
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
