package com.shiguang.message;

import com.shiguang.auth.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageSocketHandler messageSocketHandler;
    private final JwtService jwtService;

    /** 与 HTTP CORS 同一份来源白名单（app.cors.allowed-origins）：dev 默认 *，prod 收紧为站点自身来源 */
    @Value("${app.cors.allowed-origins:*}")
    private List<String> allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(messageSocketHandler, "/ws")
                .addInterceptors(new TokenHandshakeInterceptor(jwtService))
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]));
    }
}
