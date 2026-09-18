-- 拾光 M3：作品可见性（仅自己可见）
USE shiguang;

ALTER TABLE `post`
    ADD COLUMN `visibility` VARCHAR(10) NOT NULL DEFAULT 'PUBLIC'
    COMMENT 'PUBLIC / PRIVATE（仅自己可见）' AFTER `status`;