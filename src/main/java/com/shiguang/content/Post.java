package com.shiguang.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "post", autoResultMap = true)
public class Post {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private PostType type;

    private String title;

    private String description;

    /** 原始视频对象名（仅视频） */
    private String sourceObject;

    /** 转码后视频对象名 */
    private String videoObject;

    /** 封面对象名 */
    private String coverObject;

    /** 图文图片对象名列表 */
    @TableField(value = "images", typeHandler = JacksonTypeHandler.class)
    private List<String> imagesObject;

    private PostStatus status;

    private String failReason;

    private Integer likeCount;

    private Integer commentCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}