package com.shiguang.message;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class MessageSocketHandler extends TextWebSocketHandler {

    private final SocketSessions sessions;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userIdOf(session);
        if (userId != null) {
            sessions.add(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 业务都走 REST，这里只处理心跳
        if (message.getPayload().contains("\"ping\"")) {
            session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId != null) {
            sessions.remove(userId, session);
        }
    }

    private Long userIdOf(WebSocketSession session) {
        Object userId = session.getAttributes().get(TokenHandshakeInterceptor.USER_ID_KEY);
        return userId instanceof Long id ? id : null;
    }
}
