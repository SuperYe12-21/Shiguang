-- 拾光 M10：评论图片 + 私信富媒体（图片 / 作品卡片）
USE shiguang;

ALTER TABLE `comment`
    ADD COLUMN `image_urls` VARCHAR(1200) NULL
    COMMENT '图片对象名 JSON 数组（最多 3 张）' AFTER `content`;

ALTER TABLE `private_message`
    ADD COLUMN `type` VARCHAR(12) NOT NULL DEFAULT 'TEXT'
    COMMENT 'TEXT / IMAGE / POST_CARD' AFTER `content`,
    ADD COLUMN `image_urls` VARCHAR(2000) NULL
    COMMENT '图片对象名 JSON 数组（最多 9 张）' AFTER `type`,
    ADD COLUMN `post_id` BIGINT UNSIGNED NULL
    COMMENT '作品卡片指向的作品 id' AFTER `image_urls`;
