package com.shiguang.interaction;

/** 作品被点赞；只在点赞状态真的发生变化时发布 */
public record PostLikedEvent(Long postId, Long likerId) {
}
