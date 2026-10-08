-- 拾光 M11：作品播放量（有效观看计数，按用户按天去重）
USE shiguang;

ALTER TABLE `post`
    ADD COLUMN `view_count` BIGINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '有效播放数（看满 3 秒上报一次，同一用户每天最多计一次）' AFTER `comment_count`;
