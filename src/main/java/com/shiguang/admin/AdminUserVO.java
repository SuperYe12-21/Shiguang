package com.shiguang.admin;

import com.shiguang.user.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 绠＄悊鍚庡彴鐨勭敤鎴锋潯鐩紙鎸夋敞鍐屾椂闂村€掑簭灞曠ず锛?*/
@Data
@Builder
public class AdminUserVO {

    private Long id;

    private String nickname;

    /** 绠＄悊鍚戜俊鎭細渚夸簬鎸夋墜鏈哄彿纭鐢ㄦ埛韬唤 */
    private String phone;

    private String avatarUrl;

    private UserRole role;

    private Integer postCount;

    private LocalDateTime createdAt;
}
