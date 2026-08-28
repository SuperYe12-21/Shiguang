package com.shiguang.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "unit-test-secret-key-0123456789abcdef0123456789abcdef",
            Duration.ofMinutes(120),
            Duration.ofDays(14));

    @Test
    void generateAndParseAccessTokenRoundTrip() {
        String token = jwtService.createToken(42L, JwtService.TokenType.ACCESS);
        JwtService.TokenPayload payload = jwtService.parse(token, JwtService.TokenType.ACCESS);
        assertThat(payload.userId()).isEqualTo(42L);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.createToken(42L, JwtService.TokenType.ACCESS);
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");
        assertThatThrownBy(() -> jwtService.parse(tampered, JwtService.TokenType.ACCESS))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(
                "unit-test-secret-key-0123456789abcdef0123456789abcdef",
                Duration.ZERO,
                Duration.ZERO);
        String token = shortLived.createToken(7L, JwtService.TokenType.ACCESS);
        assertThatThrownBy(() -> shortLived.parse(token, JwtService.TokenType.ACCESS))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refreshTokenIsNotValidAsAccessToken() {
        String refresh = jwtService.createToken(9L, JwtService.TokenType.REFRESH);
        assertThatThrownBy(() -> jwtService.parse(refresh, JwtService.TokenType.ACCESS))
                .isInstanceOf(JwtException.class);
    }
}