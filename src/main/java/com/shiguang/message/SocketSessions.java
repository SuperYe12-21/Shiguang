package com.shiguang.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 在线 WebSocket 连接注册表：一个用户可能有多端/多标签页 */
@Slf4j
@Component
public class SocketSessions {

    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void add(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) {
            return;
        }
        set.remove(session);
        if (set.isEmpty()) {
            sessions.remove(userId);
        }
    }

    /** 给一个用户的所有连接发文本；单个连接异常不影响其他连接 */
    public void send(Long userId, String payload) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        TextMessage text = new TextMessage(payload);
        for (WebSocketSession session : set) {
            try {
                if (session.isOpen()) {
                    synchronized (session) {
                        session.sendMessage(text);
                    }
                }
            } catch (Exception e) {
                log.debug("WebSocket 推送失败，忽略: userId={} err={}", userId, e.getMessage());
            }
        }
    }
}
