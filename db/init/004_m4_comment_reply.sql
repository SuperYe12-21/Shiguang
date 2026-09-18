-- 拾光 M4：二级评论（评论回复 / 盖楼）
USE shiguang;

ALTER TABLE `comment`
    ADD COLUMN `parent_id`        BIGINT UNSIGNED NULL COMMENT '直接父评论 id，顶层评论为 NULL' AFTER `post_id`,
    ADD COLUMN `root_id`          BIGINT UNSIGNED NULL COMMENT '所属顶层评论 id，顶层评论为 NULL' AFTER `parent_id`,
    ADD COLUMN `reply_to_user_id` BIGINT UNSIGNED NULL COMMENT '被回复者 user id，仅用于展示 @' AFTER `root_id`,
    ADD KEY `idx_post_root_id` (`post_id`, `root_id`, `id`),
    ADD KEY `idx_root_id` (`root_id`, `id`);
