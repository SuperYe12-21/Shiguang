package com.shiguang.message;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PrivateMessageVO {

    private Long id;

    private Long conversationId;

    private Long senderId;

    private Long receiverId;

    private String content;

    /** TEXT / IMAGE / POST_CARD */
    private String type;

    /** 图片消息的可访问地址 */
    private List<String> images;

    /** 卡片消息的作品信息 */
    private PostCardVO post;

    private LocalDateTime createdAt;
}
