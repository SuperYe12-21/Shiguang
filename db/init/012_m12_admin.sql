-- 拾光 M12：管理员作品管理（角色 + 下架）
USE shiguang;

-- 用户角色：USER（默认） / ADMIN
ALTER TABLE `user`
    ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN' AFTER `bio`;

-- 下架原因（管理员填写，仅作者可见）
ALTER TABLE `post`
    ADD COLUMN `block_reason` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '管理员下架原因' AFTER `fail_reason`,
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'PROCESSING'
        COMMENT 'PROCESSING / PUBLISHED / FAILED / BLOCKED（管理员下架）';

-- 管理列表：按状态过滤 + 时间倒序（同时惠及首页流等 status + created_at 场景）
ALTER TABLE `post`
    ADD KEY `idx_status_created` (`status`, `created_at`);

-- 提升管理员（在本地 / 服务器上单独执行，把占位符换成实际手机号；不要提交到公开仓库）
-- UPDATE `user` SET `role` = 'ADMIN' WHERE `phone` = '<管理员手机号>';
