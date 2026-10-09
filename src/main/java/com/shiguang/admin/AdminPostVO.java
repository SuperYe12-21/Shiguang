package com.shiguang.admin;

import com.shiguang.content.PostStatus;
import com.shiguang.content.PostType;
import com.shiguang.content.PostVisibility;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理后台的作品条目（比 PostVO 多作者手机号等管理向信息） */
@Data
@Builder
public class AdminPostVO {

    private Long id;

    private PostType type;

    private PostStatus status;

    private PostVisibility visibility;

    private String title;

    private String coverUrl;

    private String blockReason;

    private String failReason;

    private Integer likeCount;

    private Integer commentCount;

    private Long viewCount;

    private LocalDateTime createdAt;

    private Long authorId;

    private String authorNickname;

    /** 管理向信息：便于按手机号确认作者身份 */
    private String authorPhone;

    private String authorAvatarUrl;
}
