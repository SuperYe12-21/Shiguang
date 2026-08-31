package com.shiguang.interaction;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CommentVO {

    private Long id;

    private Long postId;

    private Long userId;

    private String content;

    private Integer likeCount;

    private Boolean liked;

    /** 当前登录用户是否为自己发表的评论 */
    private Boolean mine;

    private Author author;

    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class Author {

        private Long id;

        private String nickname;

        private String avatarUrl;
    }
}