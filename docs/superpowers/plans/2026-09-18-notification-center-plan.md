# 通知中心实现计划

- 来源：`docs/superpowers/specs/2026-09-18-notification-center-design.md`（已获用户确认）
- 里程碑：消息系统 2 / 3（二级评论 → 通知中心 → 私信）
- 前置：`powershell -File .devtools\start-all.ps1` 拉起中间件与前后端
- 验证原则：每个任务都要跑掉对应的验证，不跳过

## 任务清单

### 1. 数据库变更

- 新增 `db/init/005_m5_notification.sql`：建 `notification` 表（含显式折叠键 `merge_key`、`uk_merge (user_id, merge_key)` 唯一键、两个查询索引）
- 在本机 MySQL 执行
- **验证**：`SHOW CREATE TABLE notification` 结构正确；两条 `merge_key` 相同的记录报重复键错误；两条 `merge_key` 为 NULL 的 FOLLOW 都能插入

### 2. 新增事件并在源头发布

- 新增 `PostLikedEvent(postId, likerId)`、`CommentLikedEvent(commentId, likerId)`（放 `interaction` 包）、`UserFollowedEvent(followerId, followeeId)`（放 `user` 包）
- `LikeService` 注入 `ApplicationEventPublisher`：`likePost` 在 `addMember` 返回 true 的分支里发 `PostLikedEvent`；`likeComment` 同理发 `CommentLikedEvent`
- `FollowService.follow`：只在真的新建关注关系时发 `UserFollowedEvent`（重复关注不发）
- **验证**：编译通过；点赞一次后日志能看到事件；取消再点赞会再发一次（去重靠通知层的行锁，不靠事件层）

### 3. 实体与 Mapper

- `Notification` 实体（含 `actorIds` 字符串字段）、`NotificationMapper extends BaseMapper<Notification>`
- `NotificationType` 枚举（`LIKE_POST` / `LIKE_COMMENT` / `COMMENT_POST` / `REPLY_COMMENT` / `FOLLOW`）与分类映射（`like` / `comment` / `follow`）
- `NotificationVO`（含 `Actor` 内嵌类）
- **验证**：编译通过

### 4. 通知写入（去重合并）

- `NotificationService` 监听四个事件，按设计文档的规则生成通知；接收者等于触发者时直接跳过
- 合并写入：事务内 `SELECT ... FOR UPDATE` 锁住 `(user_id, merge_key)` 那一行再读改写；触发者已在 `actor_ids` 里就只刷新 `actor_id` / `updated_at` / `read_at = NULL`，不在就追加并让 `actor_count + 1`
- FOLLOW 的 `merge_key` 必须写 NULL（唯一键对它不生效，所以每次关注都是新行）
- `actor_ids` 上限 100，超出后不再去重
- **验证**：同一人点赞→取消→再点赞，`actor_count` 仍为 1；换一个人点赞，变 2

### 5. 查询与已读

- `unread(userId)`：一次 `GROUP BY type` 算出 `{ total, like, comment, follow }`
- `list(userId, category, cursor, limit)`：游标格式 `<updatedAt 毫秒>_<id>`，条件 `updated_at < ?1 OR (updated_at = ?1 AND id < ?2)`，排序 `updated_at DESC, id DESC`；返回后批量补全触发者、作品封面，避免 N+1
- `markRead(userId, category)`：把该分类下 `read_at IS NULL` 的全部置为当前时间
- **验证**：三个方法各跑一次，确认总数与分类数一致、列表顺序正确

### 6. 接口层

- 新增 `NotificationController`：`GET /api/notifications/unread`、`GET /api/notifications`、`POST /api/notifications/read`
- **验证**：curl 冒烟三个接口；未登录访问返回未授权

### 7. 清理任务

- `NotificationCleaner`：`@Scheduled` 每天执行，删除 `updated_at` 早于 90 天的记录（沿用 `LikeCountFlusher` 的写法）
- **验证**：编译通过；手工插一条 91 天前的记录，触发一次后确认被删除

### 8. 前端 API 与未读数 store

- `api/notifications.js`：`fetchUnread`、`fetchNotifications`、`markNotificationsRead`
- `stores/notification.js`：`unread` 状态 + `refresh()`；登录后启动 30 秒轮询，监听 `visibilitychange` 立即补拉；登出时停止
- **验证**：浏览器控制台调用 `refresh()` 能拿到未读数；切换标签页回来会立即刷新

### 9. 导航接入红点

- 移动端 `BottomNav.vue`：「消息」改为跳转 `/notifications`，图标右上角显示未读徽标（0 不显示，>99 显示「99+」）
- PC 端 `PcSideNav.vue`：同样接上路由并在「消息」右侧显示未读数
- 新增路由 `/notifications`（`requiresAuth`）
- **验证**：有未读时两个入口都出现数字；全部已读后消失

### 10. 互动消息页

- 新增 `views/NotificationView.vue`：标题「消息」+ 三个 Tab（赞与收藏 / 评论 / 新增关注），Tab 带各自未读徽标
- 列表行：触发者头像 → 文案 → 时间；点赞行显示「A、B 等 N 人赞了你的作品」；评论/回复行在文案下显示内容摘要；右侧作品封面缩略图；未读行左侧小圆点
- 滚到底自动分页；三个 Tab 各自的空状态文案
- 切 Tab 后：拉列表 → 拉完调标记已读 → 刷新未读数
- **验证**：三个 Tab 都能渲染，空状态与非空状态都对

### 11. 点击跳转与评论定位

- `FeedView` 支持查询参数 `comment=1`（定位完成后自动打开评论面板）、`rootId`、`commentId`、`highlight=1`
- `CommentPanel` 新增 props `focusRootId` / `focusCommentId`：挂载后循环加载顶层评论分页（最多 5 页）直到找到 `focusRootId`，展开该楼层并加载到包含 `focusCommentId`（最多 5 页），滚动到该行并高亮约 2 秒；找不到就静默放弃，面板照常打开
- 通知行的点击按设计文档分流（作品 / 评论定位 / 用户主页）
- **验证**：三种通知各点一次——点赞进作品并展开评论区；回复进作品、展开对应楼层、高亮到那条回复；关注进对方主页

### 12. 整体验收与清理

- 按设计文档「验收标准」逐条走查
- 清理测试产生的通知、点赞、评论、关注记录与 Redis 计数
- **验证**：全部通过

## 完成情况（2026-09-18）

任务 1～12 全部完成并逐条验证，实施中修正了两处设计与实现的偏差：

1. **折叠唯一键**：`(user_id, type, post_id, comment_id)` 里的 NULL 列会让唯一性整体失效，`LIKE_POST` 根本折叠不起来。改用具名折叠键 `merge_key`（`post:` / `comment:` / `cpost:` / `reply:`，FOLLOW 为 NULL）。
2. **事件监听器的事务传播级别**：四个 `@EventListener` 一开始写成 `REQUIRES_NEW`，另开事务读不到调用方还没提交的评论，评论/回复通知静默丢失。改回默认传播级别（加入调用方事务），评论插入在前、通知写入在后，异常由 `guard()` 吞掉。
3. **标记已读不能刷新 `updated_at`**：`notification` 表带 `ON UPDATE CURRENT_TIMESTAMP`，`markRead` 把行顶到了列表最前面、时间显示成「刚刚」。改为在 UPDATE 里显式 `updated_at = updated_at`。
4. **通知缩略图**：图文作品 `cover_object` 为空，改为回退到 `images` 的第一张，否则图文通知右侧没有封面。

## 风险与注意

- **不要再用 `(user_id, type, post_id, comment_id)` 做唯一键**：唯一索引里一旦有 NULL 列，整行唯一性就失效，`LIKE_POST` 的 `comment_id` 恒为 NULL 会让折叠彻底失效。折叠统一走显式的 `merge_key`
- 折叠会刷新 `updated_at`，所以游标必须带 `updated_at`，不能用纯 id
- 合并写入用了行锁，事务要短，不要在事务里做远程调用
- `LikeService` 的点赞事件只能在状态真的变化时发，否则取消再点赞会重复计数（虽然行锁兜底，但没必要多发事件）
- 测试期间产生的通知、点赞、关注都要清理干净
