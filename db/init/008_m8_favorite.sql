-- 作品收藏
-- 说明：不设 favorite_count 冗余列，计数按 post_id 分组实时统计（与评论数一致），避免计数漂移。
--       收藏是私有动作，不发布事件、不写通知。
CREATE TABLE IF NOT EXISTS `post_favorite` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT UNSIGNED NOT NULL,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
    KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作品收藏';
