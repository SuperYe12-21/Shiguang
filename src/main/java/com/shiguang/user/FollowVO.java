package com.shiguang.user;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FollowVO {

    private Boolean following;

    private Long followerCount;
}