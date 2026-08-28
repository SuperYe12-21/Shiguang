package com.shiguang.user;

import java.time.LocalDateTime;

public record UserVO(
        Long id,
        String phone,
        String nickname,
        String avatarUrl,
        String bio,
        LocalDateTime createdAt) {

    public static UserVO from(User user) {
        return new UserVO(
                user.getId(),
                user.getPhone(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getCreatedAt());
    }
}