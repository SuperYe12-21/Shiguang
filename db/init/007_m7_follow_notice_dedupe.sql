-- 关注通知去重：同一人对同一个人只保留最初那一条
-- 背景：FOLLOW 通知原本 merge_key 为 NULL（不折叠），取关后再关注会不断新增「XX 关注了你」，
--       反复操作即可刷屏对方的通知列表。
-- 1) 若某组里存在未读的重复行，把保留下来的那条也置为未读，保证「该提示的那一次」不丢
UPDATE notification k
  JOIN (SELECT user_id, actor_id, MIN(id) AS keep_id, SUM(read_at IS NULL) AS unread_cnt
          FROM notification WHERE type = 'FOLLOW' GROUP BY user_id, actor_id) g
    ON g.keep_id = k.id
   SET k.read_at = NULL
 WHERE g.unread_cnt > 0;

-- 2) 删掉每组里除最早一条以外的重复行
DELETE n FROM notification n
  JOIN notification keep
    ON keep.user_id = n.user_id
   AND keep.actor_id = n.actor_id
   AND keep.type = 'FOLLOW'
   AND n.type = 'FOLLOW'
   AND keep.id < n.id;

-- 3) 给保留下来的行补上折叠键，之后由 uk_merge 唯一键兜住重复
UPDATE notification SET merge_key = CONCAT('follow:', actor_id)
 WHERE type = 'FOLLOW' AND merge_key IS NULL;
