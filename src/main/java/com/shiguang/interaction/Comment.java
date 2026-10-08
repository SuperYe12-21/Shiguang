package com.shiguang.interaction;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "comment", autoResultMap = true)
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

    /** 图片对象名列表，最多 3 张 */
    @TableField(value = "image_urls", typeHandler = JacksonTypeHandler.class)
    private List<String> imagesObject;

    private Integer likeCount;

    private LocalDateTime createdAt;
}
