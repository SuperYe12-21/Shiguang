package com.shiguang.user;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserProfileVO {

    private Long id;

    private String nickname;

    private String avatarUrl;

    private String bio;

    private LocalDateTime createdAt;

    private Long followerCount;

    private Long followingCount;

    private Long postCount;

    private Boolean followedByMe;
}