package com.shiguang.interaction;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    /** 直接父评论 id，顶层评论为 NULL */
    private Long parentId;

    /** 所属顶层评论 id，顶层评论为 NULL */
    private Long rootId;

    /** 被回复者 user id，仅用于展示 @ */
    private Long replyToUserId;

    private Long userId;

    private String content;

    private Integer likeCount;

    private LocalDateTime createdAt;
}
