-- 拾光 M9：列表可见性设置（点赞 / 收藏 / 粉丝 / 关注）
-- 没有记录 = 全部公开，新用户零成本
CREATE TABLE IF NOT EXISTS `user_privacy` (
    `user_id`              BIGINT UNSIGNED NOT NULL,
    `like_visibility`      VARCHAR(10) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC / FRIENDS / PRIVATE',
    `favorite_visibility`  VARCHAR(10) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC / FRIENDS / PRIVATE',
    `follower_visibility`  VARCHAR(10) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC / FRIENDS / PRIVATE',
    `following_visibility` VARCHAR(10) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC / FRIENDS / PRIVATE',
    `updated_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户列表可见性设置';
