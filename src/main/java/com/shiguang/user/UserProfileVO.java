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

    private Long likeCount;

    private Boolean followedByMe;

    /** 对方是否也关注了我（互关判定，用于「互相关注」文案） */
    private Boolean matched;

    /** 当前访客能看哪几项列表（点赞 / 收藏 / 粉丝 / 关注） */
    private UserPrivacyVO.Access viewerCanSee;
}
