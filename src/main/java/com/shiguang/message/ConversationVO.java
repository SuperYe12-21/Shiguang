package com.shiguang.message;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ConversationVO {

    private Long id;

    private PeerVO peer;

    /** 最后一条消息摘要（列表展示用） */
    private String lastContent;

    /** 最后一条消息的发送方，前端给「我：」加前缀 */
    private Long lastSenderId;

    private LocalDateTime lastMessageAt;

    /** 该会话里我未读的条数 */
    private Long unread;
}
