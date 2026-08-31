package com.shiguang.interaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.shiguang.common.BizException;
import com.shiguang.common.PageVO;
import com.shiguang.content.Post;
import com.shiguang.content.PostMapper;
import com.shiguang.user.User;
import com.shiguang.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    public PageVO<CommentVO> list(Long postId, Long cursorId, int limit, Long viewerId) {
        likeService.requirePublishedPost(postId);
        int size = normalizeLimit(limit);
        List<Comment> rows = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .gt(cursorId != null && cursorId > 0, Comment::getId, cursorId)
                .orderByAsc(Comment::getId)
                .last("LIMIT " + (size + 1)));
        boolean hasMore = rows.size() > size;
        List<Comment> page = hasMore ? rows.subList(0, size) : rows;

        List<CommentVO> items = toVOs(page, viewerId);
        String nextCursor = hasMore ? page.get(page.size() - 1).getId().toString() : null;
        return PageVO.<CommentVO>builder().items(items).nextCursor(nextCursor).hasMore(hasMore).build();
    }

    @Transactional
    public CommentVO create(Long postId, Long userId, String content) {
        likeService.requirePublishedPost(postId);
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            throw new BizException("评论内容不能为空");
        }
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

        eventPublisher.publishEvent(new CommentCreatedEvent(postId));
        User author = userMapper.selectById(userId);
        return toVO(comment, userId, false, 0, author);
    }

    @Transactional
    public void delete(Long commentId, Long userId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(404, "评论不存在或已删除");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BizException(403, "只能删除自己的评论");
        }
        likeService.cleanupComment(commentId);
        commentMapper.deleteById(commentId);
        postMapper.update(null, new LambdaUpdateWrapper<Post>()
                .eq(Post::getId, comment.getPostId())
                .setSql("comment_count = GREATEST(0, comment_count - 1)"));
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

    private List<CommentVO> toVOs(List<Comment> comments, Long viewerId) {
        if (comments.isEmpty()) {
            return List.of();
        }
        List<Long> ids = comments.stream().map(Comment::getId).toList();
        List<Long> userIds = comments.stream().map(Comment::getUserId).distinct().toList();
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Boolean> liked = likeService.commentLikedMap(ids, viewerId);
        Map<Long, Integer> pending = likeService.commentPendingDeltas(ids);

        return comments.stream()
                .map(comment -> toVO(comment, viewerId,
                        liked.getOrDefault(comment.getId(), false),
                        pending.getOrDefault(comment.getId(), 0),
                        users.get(comment.getUserId())))
                .toList();
    }

    private CommentVO toVO(Comment comment, Long viewerId, boolean liked, int pendingDelta, User author) {
        CommentVO.Author authorVO = author == null ? null
                : CommentVO.Author.builder()
                        .id(author.getId())
                        .nickname(author.getNickname())
                        .avatarUrl(author.getAvatarUrl())
                        .build();
        return CommentVO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .userId(comment.getUserId())
                .content(comment.getContent())
                .likeCount(Math.max(0, comment.getLikeCount() + pendingDelta))
                .liked(liked)
                .mine(viewerId != null && viewerId.equals(comment.getUserId()))
                .author(authorVO)
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}