# 二级评论实现计划

- 来源：`docs/superpowers/specs/2026-09-18-nested-comments-design.md`（已获用户确认）
- 里程碑：消息系统 1 / 3（二级评论 → 通知中心 → 私信）
- 前置：`powershell -File .devtools\start-all.ps1` 拉起中间件与前后端
- 验证原则：每个任务都要跑掉对应的验证，不跳过

## 任务清单

### 1. 数据库变更

- 新增 `db/init/004_m4_comment_reply.sql`：给 `comment` 加 `parent_id` / `root_id` / `reply_to_user_id` 三列与两个索引
- 在本机 MySQL 执行该脚本
- **验证**：`SHOW CREATE TABLE comment` 能看到三列两索引；`SELECT COUNT(*) FROM comment` 行数不变

### 2. 实体、VO、事件扩展

- `Comment` 增加 `parentId` / `rootId` / `replyToUserId`
- `CommentVO` 增加 `parentId` / `rootId` / `replyToUser{id,nickname}` / `replyCount` / `canDelete`
- `CommentCreatedEvent` 扩展为 `(postId, commentId, authorUserId, parentId, rootId, replyToUserId)`
- `CommentService.create` 发布事件时补全新参数；`FeedService` 的监听器只读 `postId`，不改
- **验证**：`mvnw.cmd -q -s .devtools\maven-settings-public.xml -DskipTests compile` 通过；发一条评论后首页缓存照常失效

### 3. 顶层评论列表改造

- `CommentService.list` 改为倒序分页：`id < cursor` + `ORDER BY id DESC`
- 一页查完后按 `root_id IN (...)` 做一次 `GROUP BY`，得到每条顶层评论的 `replyCount`（不引入冗余计数字段）
- 计算 `canDelete`：取作品作者 id，`评论作者 == 当前用户 || 作品作者 == 当前用户`
- **验证**：curl 拉评论列表，顺序为最新在前；给某条顶层评论加一条回复后，该条的 `replyCount` 变 1

### 4. 回复列表接口

- `CommentService.listReplies(commentId, cursor, limit, viewerId)`：先把传入 id 解析成所属顶层评论 id，再按 `root_id = ? AND id > cursor ORDER BY id ASC` 分页
- Controller 新增 `GET /api/comments/{id}/replies`
- **验证**：curl 分别传顶层评论 id 和某条回复 id，两次返回结果一致

### 5. 创建回复接口

- `CommentService.createReply(commentId, userId, content)`：
  - 查被回复的评论，不存在 → 404「评论不存在或已删除」
  - `requireAccessiblePost` 校验作品可访问（私密作品对非作者仍拦截）
  - `rootId = 被回复评论.rootId != null ? 被回复评论.rootId : 被回复评论.id`
  - `replyToUserId = 被回复评论.userId`（任何回复场景都写入）
  - 写库 → `comment_count + 1` → 发 `CommentCreatedEvent`
- Controller 新增 `POST /api/comments/{id}/replies`
- **验证**：回复顶层评论，新行 `root_id` = 顶层 id；再回复这条回复，新行 `root_id` 仍等于顶层 id（不产生第三层）

### 6. 删除权限 + 级联

- 允许删除的条件：评论作者本人 或 作品作者
- 删顶层评论：先查 `root_id = 该评论 id` 的回复，逐个清理点赞状态后批量删除；`comment_count -= (1 + 实际删除的回复数)`
- 删回复：只删这一条；`comment_count -= 1`
- 两点都要发 `CommentDeletedEvent`
- **验证**：用A账号评论并盖楼，再用作品作者账号删除顶层评论，确认回复一并消失且 `comment_count` 数值正确

### 7. 前端 API 封装

- `api/comments.js` 增加 `fetchReplies(commentId, cursor, limit)` 和 `createReply(commentId, content)`
- **验证**：浏览器控制台调用能拿到数据

### 8. 评论面板：折叠与展开

- 顶层列表改倒序渲染，新发的评论插入顶部
- 每条顶层评论显示「查看 N 条回复」（`replyCount > 0` 才显示）
- 点击展开：拉前 10 条正序渲染，底部「查看更多回复」按 cursor 续拉；再点标题收起并清空已加载的回复
- 回复项渲染：`replyToUser` 存在时正文前加「回复 @某某：」
- **验证**：移动端 + PC 端各看一遍，折叠、展开、继续分页、收起都正常

### 9. 评论面板：回复输入、作者标签、删除

- 顶层评论和回复的操作行都加「回复」按钮
- 点击进入回复模式：输入框上方出现「回复 @某某」提示条（带取消按钮）；发送成功后清空、自动展开该楼层
- 昵称后加「作者」小标签（`c.userId === post.userId` 时）
- 删除按钮改由后端返回的 `canDelete` 控制，不再用 `mine`
- **验证**：手工走三条链路——回复顶层评论 / 回复某条回复 / 作品作者删别人的评论

### 10. 整体验收

- 按设计文档「验收标准」逐条走查
- **验证**：全部通过后清理测试数据

## 风险与注意

- 顶层评论改为倒序是刻意的行为变更，不是回归
- 存量评论的 `parent_id` / `root_id` 都是 NULL，前端一律按 `root_id == null` 判断顶层，不要依赖后端补值
- `CommentCreatedEvent` 正在被 `FeedService` 用于首页缓存失效，扩字段时别改动该监听器的读取字段
- 测试期间产生的评论、回复、点赞要清理干净
