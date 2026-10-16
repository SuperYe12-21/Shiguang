-- 在停止旧版本写入后执行；必须先完成 001—012。重复执行不会覆盖已有登记或删除标记。
-- 仅回填符合旧服务端 UUID 命名规则、所有引用指向同一用户的 key。
-- 多用户共用、完整 URL、异常 key、没有业务引用的旧对象均保持只读，不授予绑定或删除权限。
-- 单一业务持有人是历史数据能提供的最大证据，并非上传审计证明；上线前应审查候选结果。
USE shiguang;

CREATE TABLE IF NOT EXISTS storage_upload (
    object_name VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    uploader_id BIGINT UNSIGNED NOT NULL,
    purpose VARCHAR(10) NOT NULL,
    content_type VARCHAR(100) NULL,
    max_size BIGINT UNSIGNED NOT NULL,
    bound TINYINT(1) NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (object_name),
    KEY idx_upload_uploader (uploader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传对象归属；NULL MIME 仅用于经审查的历史回填';

DROP TEMPORARY TABLE IF EXISTS sg_upload_legacy_refs;
CREATE TEMPORARY TABLE sg_upload_legacy_refs (
    object_name VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    owner_id BIGINT UNSIGNED NOT NULL,
    purpose VARCHAR(10) NOT NULL
);

INSERT INTO sg_upload_legacy_refs
SELECT source_object, user_id, 'VIDEO' FROM post WHERE source_object <> ''
UNION ALL SELECT video_object, user_id, 'VIDEO' FROM post WHERE video_object <> ''
UNION ALL SELECT cover_object, user_id, 'COVER' FROM post WHERE cover_object <> ''
UNION ALL SELECT avatar_url, id, 'IMAGE' FROM user WHERE avatar_url <> '';

INSERT INTO sg_upload_legacy_refs
SELECT media.object_name, post.user_id, 'IMAGE'
FROM post JOIN JSON_TABLE(
    IF(JSON_TYPE(post.images) = 'ARRAY', post.images, JSON_ARRAY()),
    '$[*]' COLUMNS (object_name VARCHAR(500) PATH '$' NULL ON ERROR)
) AS media WHERE media.object_name IS NOT NULL;

INSERT INTO sg_upload_legacy_refs
SELECT media.object_name, comment.user_id, 'IMAGE'
FROM comment JOIN JSON_TABLE(
    IF(JSON_VALID(comment.image_urls), comment.image_urls, JSON_ARRAY()),
    '$[*]' COLUMNS (object_name VARCHAR(500) PATH '$' NULL ON ERROR)
) AS media WHERE media.object_name IS NOT NULL;

INSERT INTO sg_upload_legacy_refs
SELECT media.object_name, private_message.sender_id, 'IMAGE'
FROM private_message JOIN JSON_TABLE(
    IF(JSON_VALID(private_message.image_urls), private_message.image_urls, JSON_ARRAY()),
    '$[*]' COLUMNS (object_name VARCHAR(500) PATH '$' NULL ON ERROR)
) AS media WHERE media.object_name IS NOT NULL;

INSERT INTO storage_upload (object_name, uploader_id, purpose, content_type, max_size, bound)
SELECT refs.object_name, MIN(refs.owner_id),
       CASE WHEN MAX(refs.purpose = 'VIDEO') = 1 THEN 'VIDEO'
            WHEN MAX(refs.purpose = 'IMAGE') = 1 THEN 'IMAGE' ELSE 'COVER' END,
       NULL,
       CASE WHEN MAX(refs.purpose = 'VIDEO') = 1 THEN 524288000 ELSE 20971520 END,
       1
FROM sg_upload_legacy_refs AS refs
LEFT JOIN storage_upload AS registered ON registered.object_name = refs.object_name
WHERE registered.object_name IS NULL
GROUP BY refs.object_name
HAVING COUNT(DISTINCT refs.owner_id) = 1
   AND MIN(refs.owner_id) > 0
   AND (MAX(refs.purpose = 'VIDEO') = 0 OR MIN(refs.purpose = 'VIDEO') = 1)
   AND REGEXP_LIKE(refs.object_name,
       '^(images|videos|covers)/([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]+)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}[.](jpg|jpeg|png|webp|gif|heic|heif|mp4|mov|avi|webm)$', 'c');

DROP TEMPORARY TABLE sg_upload_legacy_refs;
