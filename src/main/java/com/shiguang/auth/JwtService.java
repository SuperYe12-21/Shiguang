package com.shiguang.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    public enum TokenType {
        ACCESS,
        REFRESH
    }

    public record TokenPayload(Long userId, TokenType type) {
    }

    private final SecretKey key;
    private final Duration accessExpire;
    private final Duration refreshExpire;

    @Autowired
    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.access-expire-minutes:120}") long accessExpireMinutes,
                      @Value("${jwt.refresh-expire-days:14}") long refreshExpireDays) {
        this(secret, Duration.ofMinutes(accessExpireMinutes), Duration.ofDays(refreshExpireDays));
    }

    public JwtService(String secret, Duration accessExpire, Duration refreshExpire) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpire = accessExpire;
        this.refreshExpire = refreshExpire;
    }

    public String createToken(Long userId, TokenType type) {
        Duration ttl = type == TokenType.ACCESS ? accessExpire : refreshExpire;
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public TokenPayload parse(String token, TokenType expected) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String type = claims.get("type", String.class);
        if (!expected.name().equals(type)) {
            throw new JwtException("token type mismatch");
        }
        return new TokenPayload(Long.valueOf(claims.getSubject()), expected);
    }
}