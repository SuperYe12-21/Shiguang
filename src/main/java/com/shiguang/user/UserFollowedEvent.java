package com.shiguang.user;

/** 新增关注；重复关注不发布 */
public record UserFollowedEvent(Long followerId, Long followeeId) {
}
