package com.shiguang.feed;

/** 朋友动态红点：latestPostId 为好友最新作品，seenPostId 为上次查看时记录的 id */
public record FriendsUnreadVO(boolean unread, Long latestPostId, Long seenPostId) {
}
