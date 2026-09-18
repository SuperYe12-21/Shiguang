package com.shiguang.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收者 */
    private Long userId;

    private NotificationType type;

    /** 折叠键：按作品/评论折叠的类型填值并受唯一键约束，不折叠的类型为 NULL */
    private String mergeKey;

    /** 最近一个触发者 */
    private Long actorId;

    /** 去重后的人数 */
    private Integer actorCount;

    /** 触发者 id 的 JSON 数组，最多 100 个，仅用于去重与折叠展示 */
    private String actorIds;

    private Long postId;

    private Long commentId;

    /** 所属顶层评论，回复通知用于定位楼层 */
    private Long rootId;

    /** 评论/回复摘要 */
    private String content;

    /** 已读时间，NULL 表示未读 */
    private LocalDateTime readAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
