package com.shiguang.message;

import lombok.Builder;
import lombok.Data;

/** 会话/聊天页展示的对方用户信息 */
@Data
@Builder
public class PeerVO {

    private Long id;

    private String nickname;

    private String avatarUrl;
}
