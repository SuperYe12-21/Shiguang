-- 拾光 M5：通知中心
USE shiguang;

CREATE TABLE IF NOT EXISTS `notification` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '接收者',
    `type`        VARCHAR(20)  NOT NULL COMMENT 'LIKE_POST / LIKE_COMMENT / COMMENT_POST / REPLY_COMMENT / FOLLOW',
    `merge_key`   VARCHAR(64) NULL COMMENT '折叠键：按作品/评论折叠的类型填值并受唯一键约束；不折叠的类型（FOLLOW）为 NULL',
    `actor_id`    BIGINT UNSIGNED NOT NULL COMMENT '最近一个触发者',
    `actor_count` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '去重后的人数',
    `actor_ids`   VARCHAR(1200) NULL COMMENT '触发者 id 的 JSON 数组，最多 100 个，仅用于去重与折叠展示',
    `post_id`     BIGINT UNSIGNED NULL COMMENT '关联作品',
    `comment_id`  BIGINT UNSIGNED NULL COMMENT '关联评论',
    `root_id`     BIGINT UNSIGNED NULL COMMENT '所属顶层评论，回复通知用于定位楼层',
    `content`     VARCHAR(500) NULL COMMENT '评论/回复摘要',
    `read_at`     DATETIME NULL COMMENT '已读时间，NULL 表示未读',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merge` (`user_id`, `merge_key`),
    KEY `idx_user_updated` (`user_id`, `updated_at`),
    KEY `idx_user_read` (`user_id`, `read_at`)
) ENGINE=InnoDB COMMENT='通知';
