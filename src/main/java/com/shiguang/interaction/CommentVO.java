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