package com.shiguang.notification;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class NotificationVO {

    private Long id;

    private String type;

    private String category;

    private Boolean read;

    /** 最近一个触发者 */
    private Actor actor;

    private Integer actorCount;

    /** 最多 3 个触发者，用于折叠展示 */
    private List<Actor> actors;

    private Long postId;

    private Long commentId;

    private Long rootId;

    private String content;

    /** 作品封面缩略图；作品已删除时为 null */
    private String postCoverUrl;

    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class Actor {

        private Long id;

        private String nickname;

        private String avatarUrl;
    }
}
