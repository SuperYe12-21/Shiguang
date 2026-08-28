-- 拾光 M2：作品表结构升级（对已有库执行）
USE shiguang;

ALTER TABLE `post`
    ADD COLUMN `source_object` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '原始视频对象名' AFTER `description`,
    ADD COLUMN `video_object`  VARCHAR(500) NOT NULL DEFAULT '' COMMENT '转码后视频对象名' AFTER `source_object`,
    ADD COLUMN `cover_object`  VARCHAR(500) NOT NULL DEFAULT '' COMMENT '封面对象名' AFTER `video_object`,
    ADD COLUMN `fail_reason`   VARCHAR(500) NOT NULL DEFAULT '' COMMENT '失败原因' AFTER `status`,
    DROP COLUMN `cover_url`,
    DROP COLUMN `video_url`;
