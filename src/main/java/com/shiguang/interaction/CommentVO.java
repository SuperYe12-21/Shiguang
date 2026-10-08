package com.shiguang.interaction;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CommentVO {

    private Long id;

    private Long postId;

    private Long parentId;

    private Long rootId;

    /** 被回复者，用于渲染「回复 @某某」 */
    private ReplyToUser replyToUser;

    /** 顶层评论下的回复数，回复自身为 null */
    private Integer replyCount;

    /** 当前用户是否有权删除（评论作者本人或作品作者） */
    private Boolean canDelete;

    private Long userId;

    private String content;

    /** 评论图片（续期后的可访问地址） */
    private List<String> images;

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

    @Data
    @Builder
    public static class ReplyToUser {

        private Long id;

        private String nickname;
    }
}
