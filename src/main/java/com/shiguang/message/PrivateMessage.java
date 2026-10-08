package com.shiguang.message;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "private_message", autoResultMap = true)
public class PrivateMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private Long senderId;

    private Long receiverId;

    private String content;

    /** TEXT / IMAGE / POST_CARD */
    private MessageType type;

    /** 图片对象名列表，最多 9 张 */
    @TableField(value = "image_urls", typeHandler = JacksonTypeHandler.class)
    private List<String> imagesObject;

    /** 作品卡片指向的作品 */
    private Long postId;

    private LocalDateTime createdAt;
}
