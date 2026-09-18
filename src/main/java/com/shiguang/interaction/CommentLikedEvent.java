package com.shiguang.interaction;

/** 评论被点赞；只在点赞状态真的发生变化时发布 */
public record CommentLikedEvent(Long commentId, Long likerId) {
}
