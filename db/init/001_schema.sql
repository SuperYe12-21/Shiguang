-- 拾光（Shiguang）数据库初始化脚本
-- MySQL 8.x / utf8mb4

CREATE DATABASE IF NOT EXISTS shiguang DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE shiguang;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `phone`       VARCHAR(20)     NOT NULL COMMENT '手机号（唯一）',
    `nickname`    VARCHAR(50)     NOT NULL COMMENT '昵称',
    `avatar_url`  VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '头像地址',
    `bio`         VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '个人简介',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB COMMENT='用户';

-- 关注关系表
CREATE TABLE IF NOT EXISTS `follow` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `follower_id` BIGINT UNSIGNED NOT NULL COMMENT '关注者',
    `followee_id` BIGINT UNSIGNED NOT NULL COMMENT '被关注者',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follow` (`follower_id`, `followee_id`),
    KEY `idx_followee` (`followee_id`)
) ENGINE=InnoDB COMMENT='关注关系';

-- 作品表
CREATE TABLE IF NOT EXISTS `post` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '作者',
    `type`          VARCHAR(10)     NOT NULL COMMENT 'VIDEO / IMAGE',
    `title`         VARCHAR(100)    NOT NULL DEFAULT '' COMMENT '标题',
    `description`   VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '描述',
    `source_object` VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '原始视频对象名',
    `video_object`  VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '转码后视频对象名',
    `cover_object`  VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '封面对象名',
    `images`        JSON            NULL COMMENT '图文图片列表',
    `status`        VARCHAR(20)     NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING / PUBLISHED / FAILED',
    `fail_reason`   VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '失败原因',
    `like_count`    INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '点赞数（冗余）',
    `comment_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '评论数（冗余）',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`),
    KEY `idx_created` (`created_at`)
) ENGINE=InnoDB COMMENT='作品';

-- 作品点赞表
CREATE TABLE IF NOT EXISTS `post_like` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT UNSIGNED NOT NULL,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
    KEY `idx_user` (`user_id`)
) ENGINE=InnoDB COMMENT='作品点赞';

-- 评论表
CREATE TABLE IF NOT EXISTS `comment` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT UNSIGNED NOT NULL,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `content`    VARCHAR(1000)   NOT NULL COMMENT '评论内容',
    `like_count` INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '评论点赞数（冗余）',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_post_created` (`post_id`, `created_at`)
) ENGINE=InnoDB COMMENT='评论';

-- 评论点赞表
CREATE TABLE IF NOT EXISTS `comment_like` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `comment_id` BIGINT UNSIGNED NOT NULL,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`)
) ENGINE=InnoDB COMMENT='评论点赞';
