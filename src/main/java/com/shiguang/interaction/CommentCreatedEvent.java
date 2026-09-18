package com.shiguang.interaction;

public record CommentCreatedEvent(Long postId, Long commentId, Long authorUserId,
                                  Long parentId, Long rootId, Long replyToUserId) {
}
