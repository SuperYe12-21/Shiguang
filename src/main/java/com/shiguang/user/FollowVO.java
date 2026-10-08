package com.shiguang.user;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FollowVO {

    private Boolean following;

    private Long followerCount;

    /** 对方是否也关注了我 */
    private Boolean matched;
}
