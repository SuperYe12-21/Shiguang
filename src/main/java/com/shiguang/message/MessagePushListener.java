package com.shiguang.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/** 私信提交后推送给在线的双方（多端同步）；离线端靠进入页面时拉取 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessagePushListener {

    private final SocketSessions sessions;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(PrivateMessageSentEvent event) {
        // 接收方多带一份发送者资料，前端可以直接弹「谁发了什么」的提示
        String toReceiver = json(payload(event.message(), messageService.peerOf(event.senderId())));
        if (toReceiver != null) {
            sessions.send(event.receiverId(), toReceiver);
        }
        String toSender = json(payload(event.message(), null));
        if (toSender != null) {
            sessions.send(event.senderId(), toSender);
        }
        long unread = messageService.unreadCount(event.receiverId());
        String unreadJson = json(Map.of("type", "unread", "data", Map.of("messages", unread)));
        if (unreadJson != null) {
            sessions.send(event.receiverId(), unreadJson);
        }
    }

    private Map<String, Object> payload(PrivateMessageVO message, PeerVO peer) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "message");
        map.put("data", message);
        map.put("peer", peer);
        return map;
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("WebSocket 推送序列化失败: {}", e.getMessage());
            return null;
        }
    }
}
