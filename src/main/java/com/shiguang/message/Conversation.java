package com.shiguang.message;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 私信会话：一对用户一条，userAId 恒为较小的一方，保证 (userAId, userBId) 唯一 */
@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 较小的用户 id */
    private Long userAId;

    /** 较大的用户 id */
    private Long userBId;

    private Long lastMessageId;

    private LocalDateTime lastMessageAt;

    /** A 已读到的最大消息 id */
    private Long aLastReadId;

    /** B 已读到的最大消息 id */
    private Long bLastReadId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
