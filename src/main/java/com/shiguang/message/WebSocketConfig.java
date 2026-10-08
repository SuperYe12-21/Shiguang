package com.shiguang.message;

import com.shiguang.auth.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageSocketHandler messageSocketHandler;
    private final JwtService jwtService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(messageSocketHandler, "/ws")
                .addInterceptors(new TokenHandshakeInterceptor(jwtService))
                .setAllowedOriginPatterns("*");
    }
}
