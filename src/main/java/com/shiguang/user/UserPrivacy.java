package com.shiguang.user;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_privacy")
public class UserPrivacy {

    @TableId
    private Long userId;

    private String likeVisibility;

    private String favoriteVisibility;

    private String followerVisibility;

    private String followingVisibility;

    private LocalDateTime updatedAt;
}
