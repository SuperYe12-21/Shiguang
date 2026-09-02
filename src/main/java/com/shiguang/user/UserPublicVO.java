package com.shiguang.user;

import java.time.LocalDateTime;

public record UserPublicVO(
        Long id,
        String nickname,
        String avatarUrl,
        String bio,
        LocalDateTime createdAt,
        Boolean followedByMe) {
}
