package com.shiguang.auth.dto;

import com.shiguang.user.UserVO;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserVO user) {
}