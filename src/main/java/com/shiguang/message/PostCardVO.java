package com.shiguang.message;

import lombok.Builder;
import lombok.Data;

/** 私信里的作品卡片 */
@Data
@Builder
public class PostCardVO {

    private Long id;

    private String title;

    /** VIDEO / IMAGE */
    private String type;

    private String coverUrl;

    private Long authorId;

    private String authorNickname;

    /** 作品是否还在：被删除时前端显示「作品已删除」 */
    private Boolean available;
}
