package com.shiguang.content;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PostVO {

    private Long id;

    private PostType type;

    private String title;

    private String description;

    private PostStatus status;

    private String videoUrl;

    private String coverUrl;

    private List<String> images;

    private Integer likeCount;

    private Integer commentCount;

    private Boolean liked;

    private String failReason;

    private Author author;

    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class Author {

        private Long id;

        private String nickname;

        private String avatarUrl;

        private Boolean following;
    }
}