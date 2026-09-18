# 二级评论设计（评论回复 / 盖楼）

日期：2026-09-18
所属里程碑：消息系统 1 / 3（二级评论 → 通知中心 → 私信）
状态：已确认，已实现

## 目标

评论区目前是平铺的一层：谁都能评论，但不能回复某个人。本期把评论改成两级盖楼：

1. 顶层评论平铺，最新在前
2. 每条顶层评论下可挂回复，**回复默认折叠**，点击才展开
3. 回复某人时正文前显示「回复 @某某：」
4. 删除权限放开到作品作者

**本期不建通知表。** 但会把评论事件的参数补全（`parentId` / `rootId` / `replyToUserId`），里程碑 2 的通知中心直接消费这个事件就能生成「某某回复了你」，届时评论侧无需返工。

非目标（以后再说）：@提及、无限嵌套、评论置顶、评论举报、评论内容审核。

## 已确认的产品决策

| 决策点 | 结论 |
|---|---|
| 层级 | 两级。回复的回复也挂在同一顶层评论下，不再分层 |
| 回复展示 | 默认折叠。顶层评论下方显示「查看 N 条回复」，点击才加载展开 |
| 排序 | 顶层评论按时间**倒序**（最新在前）；回复按时间**正序**（最早在上） |
| @前缀 | 回复的是一条**回复**时，正文前显示「回复 @某某：」；回复顶层评论不显示 |
| 删除 | 本人可删自己的评论/回复；**作品作者可删自己作品下的任何评论**；删顶层评论时整层（含回复）一起删 |
| 展开分页 | 展开后每次加载 10 条回复，底部「查看更多回复」继续分页 |

## 后端设计

### 数据变更

新增 `db/init/004_m4_comment_reply.sql`：

```sql
ALTER TABLE `comment`
    ADD COLUMN `parent_id`        BIGINT UNSIGNED NULL COMMENT '直接父评论 id，顶层评论为 NULL' AFTER `post_id`,
    ADD COLUMN `root_id`          BIGINT UNSIGNED NULL COMMENT '所属顶层评论 id，顶层评论为 NULL' AFTER `parent_id`,
    ADD COLUMN `reply_to_user_id` BIGINT UNSIGNED NULL COMMENT '被回复者 user id，仅用于展示 @' AFTER `root_id`,
    ADD KEY `idx_post_root_id` (`post_id`, `root_id`, `id`),
    ADD KEY `idx_root_id` (`root_id`, `id`);
```

- 现有索引 `idx_post_created (post_id, created_at)` 保留；顶层评论查询改走 `idx_post_root_id`。
- 存量数据全是顶层评论（两个新字段为 NULL），无需数据迁移。

### 为什么不加 reply_count 冗余字段

折叠入口需要知道每条顶层评论下有几条回复。用「每页一次 `GROUP BY`」拿到：

```sql
SELECT root_id, COUNT(*) FROM comment WHERE root_id IN (...) GROUP BY root_id
```

一页 20 条顶层评论只多一次查询，且永远不会出现计数漂移——删除、级联删除、作品删除都不需要额外维护计数。

### 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/posts/{id}/comments` | 顶层评论分页，时间倒序；每项带 `replyCount`、`canDelete` |
| GET | `/api/comments/{id}/replies` | 某楼层的回复分页，时间正序。`{id}` 可传顶层评论 id 或任意回复 id，服务端统一解析到所属顶层评论 |
| POST | `/api/comments/{id}/replies` | 回复评论。body `{"content":"..."}`；`{id}` 是被回复的那条评论（顶层或回复皆可），服务端解析出 `rootId` 与 `replyToUserId` |
| POST | `/api/posts/{id}/comments` | 不变，新增顶层评论 |
| DELETE | `/api/comments/{id}` | 权限扩展 + 级联删除，见下 |

分页沿用现有 cursor 风格：顶层评论 `id < cursor`，回复 `id > cursor`。

### 删除规则

允许删除的条件：**当前用户 == 评论作者** 或 **当前用户 == 作品作者**。

- 删顶层评论：连同 `root_id = 该评论 id` 的所有回复一起删；`post.comment_count` 减去 `1 + 实际删除的回复数`
- 删回复：只删这一条；`post.comment_count` 减 1
- 被删评论的点赞状态一并清理（复用 `likeService.cleanupComment`）
- 删作品时的级联清理逻辑不变（仍按 `post_id` 全删）

### 事件

`CommentCreatedEvent` 从 `(postId)` 扩展为：

```java
public record CommentCreatedEvent(Long postId, Long commentId, Long authorUserId,
                                  Long parentId, Long rootId, Long replyToUserId) {}
```

`FeedService` 现有的两个监听器只读 `postId`，签名和逻辑都不变。里程碑 2 的通知服务订阅同一个事件即可生成回复通知。

### VO 变更

`CommentVO` 增加：

- `parentId`、`rootId`：前端判断层级
- `replyToUser`：`{ id, nickname }`，渲染「回复 @某某」
- `replyCount`：仅顶层评论返回
- `canDelete`：当前用户可删（本人或作品作者）

## 前端设计

### 评论面板

`CommentPanel.vue` 原地重构，不拆文件：

- 顶层列表时间倒序；新发的评论插入顶部
- 每条顶层评论：头像 / 昵称 / 时间 / 正文 / 点赞 / 回复 / 删除
- 折叠区：`replyCount > 0` 时显示「查看 N 条回复」；点击展开后正序渲染回复，每次 10 条，底部「查看更多回复」继续加载；再点标题收起
- 回复项：头像 / 昵称 / 时间 / 正文（被回复对象是回复时带「回复 @某某：」）/ 点赞 / 回复 / 删除
- 底栏输入框：点「回复」后进入回复模式（输入框上方出现可取消的「回复 @某某」提示条），发送成功后清空、自动展开该楼层并定位到新回复
- 作品作者的评论和回复，昵称后加一个「作者」小标签

### API 封装（`api/comments.js`）

```js
export const fetchReplies = (commentId, cursor, limit = 10) => ...
export const createReply = (commentId, content) => ...
```

### 计数

`post.commentCount` 是包含回复的总数，标题栏直接展示，不额外计算。

## 边界与决策

1. 回复只存在于两级内：`root_id` 永远指向顶层评论，不存在第三层。
2. `reply_to_user_id` 在任何回复场景下都会写入（里程碑 2 的通知靠它定位收件人），但只有「回复的是一条回复」时才渲染 @前缀。被回复的评论已被删除时该字段仍保留，照常显示 @，不去查那条评论实体。
3. `replyCount` 为 0 时不显示折叠入口，避免出现「查看 0 条回复」。
4. 展开状态下删掉整层：已展开的回复一起从界面移除，计数按实际删除数量扣减。
5. 顶层评论改为倒序是刻意的行为变更，不是 bug。
6. 本期不产生任何通知；转发通知的落库与展示在里程碑 2。

## 验收标准

- 发顶层评论：出现在列表最上方，「评论 N」计数 +1。
- 回复顶层评论：该楼层出现「查看 1 条回复」，展开后看到回复，计数 +1。
- 回复某条回复：新回复仍在该顶层楼层内，正文前显示「回复 @某某：」，不产生第三层。
- 回复默认折叠：关掉评论面板再打开，所有回复都回到折叠状态。
- 删除权限：作品作者能看到别人评论上的删除按钮；非作者、非本人的评论看不到删除按钮。
- 删除顶层评论：其下所有回复一并消失，折叠入口不再显示，计数按实际删除数扣减。
- 分页：顶层评论滚到底部继续加载；某楼层回复超过 10 条时出现「查看更多回复」。
- 作品删除时，其全部评论与回复按现有逻辑清理。
