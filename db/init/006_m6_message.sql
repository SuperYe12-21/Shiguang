-- 拾光 M6：私信 IM
USE shiguang;

CREATE TABLE IF NOT EXISTS `conversation` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_a_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户A（约定 id 较小的一侧）',
    `user_b_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户B（id 较大的一侧）',
    `last_message_id` BIGINT UNSIGNED NULL COMMENT '最后一条消息 id',
    `last_message_at` DATETIME NULL COMMENT '最后一条消息时间，会话列表排序键',
    `a_last_read_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'A 已读到的最大消息 id',
    `b_last_read_id`  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'B 已读到的最大消息 id',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pair` (`user_a_id`, `user_b_id`),
    KEY `idx_a_active` (`user_a_id`, `last_message_at`),
    KEY `idx_b_active` (`user_b_id`, `last_message_at`)
) ENGINE=InnoDB COMMENT='私信会话';

CREATE TABLE IF NOT EXISTS `private_message` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `conversation_id` BIGINT UNSIGNED NOT NULL COMMENT '所属会话',
    `sender_id`       BIGINT UNSIGNED NOT NULL COMMENT '发送方',
    `receiver_id`     BIGINT UNSIGNED NOT NULL COMMENT '接收方',
    `content`         VARCHAR(1000)   NOT NULL COMMENT '文本内容',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_conv` (`conversation_id`, `id`),
    KEY `idx_receiver` (`receiver_id`, `id`)
) ENGINE=InnoDB COMMENT='私信消息';
