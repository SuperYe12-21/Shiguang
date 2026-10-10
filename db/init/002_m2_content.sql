-- 拾光 M2：作品表结构升级（把 cover_url / video_url 换成对象名，新增转码相关列）
--
-- 这个脚本要同时兼容两种情况：
--   1) 老库升级：001 的旧版本里有 cover_url / video_url，需要 ADD 新列 + DROP 旧列
--   2) 全新安装：001 已经是最新结构（直接包含新列、没有旧列），此时本脚本什么都不做
-- 所以用 information_schema 判断后再执行，避免全新安装时 "Can't DROP 'cover_url'" 直接中断部署。
USE shiguang;

DROP PROCEDURE IF EXISTS `sg_m2_upgrade`;

DELIMITER $$
CREATE PROCEDURE `sg_m2_upgrade`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post' AND COLUMN_NAME = 'source_object') THEN
        ALTER TABLE `post`
            ADD COLUMN `source_object` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '原始视频对象名' AFTER `description`,
            ADD COLUMN `video_object`  VARCHAR(500) NOT NULL DEFAULT '' COMMENT '转码后视频对象名' AFTER `source_object`,
            ADD COLUMN `cover_object`  VARCHAR(500) NOT NULL DEFAULT '' COMMENT '封面对象名' AFTER `video_object`,
            ADD COLUMN `fail_reason`   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '失败原因' AFTER `status`;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post' AND COLUMN_NAME = 'cover_url') THEN
        ALTER TABLE `post` DROP COLUMN `cover_url`;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post' AND COLUMN_NAME = 'video_url') THEN
        ALTER TABLE `post` DROP COLUMN `video_url`;
    END IF;
END$$
DELIMITER ;

CALL `sg_m2_upgrade`();
DROP PROCEDURE IF EXISTS `sg_m2_upgrade`;
