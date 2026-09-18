package com.shiguang.notification;

import lombok.Builder;
import lombok.Data;

/** 未读数：总数给底部导航徽标，分项给消息页的 Tab 徽标 */
@Data
@Builder
public class UnreadCountVO {

    private long total;

    private long like;

    private long comment;

    private long follow;
}
