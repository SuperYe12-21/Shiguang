package com.shiguang.message;

import com.shiguang.auth.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** WebSocket 握手鉴权：浏览器不能带自定义头，token 走 query 参数 */
@Slf4j
@RequiredArgsConstructor
public class TokenHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_KEY = "sgUserId";

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = queryParam(request.getURI().getRawQuery(), "token");
        if (token == null) {
            return false;
        }
        try {
            JwtService.TokenPayload payload = jwtService.parse(token, JwtService.TokenType.ACCESS);
            attributes.put(USER_ID_KEY, payload.userId());
            return true;
        } catch (Exception e) {
            log.debug("WebSocket 握手 token 无效: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }
        for (String part : rawQuery.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0 && name.equals(part.substring(0, idx))) {
                return URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
