package com.shiguang.message;

/** 私信落库后发布；事务提交后由监听器推送给在线端 */
public record PrivateMessageSentEvent(Long senderId, Long receiverId, PrivateMessageVO message) {
}
