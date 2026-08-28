package com.shiguang.user;

import java.time.LocalDateTime;

public record UserPublicVO(
        Long id,
        String nickname,
        String avatarUrl,
        String bio,
        LocalDateTime createdAt) {

    public static UserPublicVO from(User user) {
        return new UserPublicVO(
                user.getId(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getCreatedAt());
    }
}