package com.shiguang.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称最长 50 个字符")
        String nickname,

        @Size(max = 500, message = "头像地址过长")
        String avatarUrl,

        @Size(max = 200, message = "简介最长 200 个字符")
        String bio) {
}