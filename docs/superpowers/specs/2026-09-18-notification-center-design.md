# 通知中心设计

日期：2026-09-18
所属里程碑：消息系统 2 / 3（二级评论 → 通知中心 → 私信）
状态：待用户确认

## 目标

把「点赞、评论、回复、关注」这些散落的互动聚合成一个可查看、可跳转的通知中心，并把底部导航的「消息」入口真正接通。

本期交付：

1. 互动消息页（三个 Tab：赞与收藏 / 评论 / 新增关注），底部导航的「消息」直接进入
2. 未读红点（底部导航 + PC 侧栏），显示总未读数，超过 99 显示「99+」
3. 分类未读徽标（Tab 上）
4. 点通知精确跳转（作品 / 评论区 / 具体回复 / 对方主页）
5. 点赞通知按作品折叠

**本期不做消息页外壳。** 私信和消息请求属于里程碑 3，届时在互动消息页外面加一层入口行把它变成二级页——互动消息页原样复用，不返工。

非目标：@提及、通知设置开关、推送（App/浏览器通知）、收藏通知（收藏功能未做）。

## 已确认的产品决策

| 决策点 | 结论 |
|---|---|
| 通知范围 | 作品被点赞、评论被点赞、作品被评论、评论被回复、被关注；自己的操作一律不通知自己 |
| 折叠规则 | **只有点赞折叠**，按「作品」和「评论」分别永久合并成一行；评论、回复、关注各自独立成行 |
| 折叠展示 | 一行最多展示 3 个昵称，其余用「等 N 人」省略 |
| 取消点赞 | 不产生通知，也不删除已有通知（通知是历史记录，不是实时状态） |
| 跳转 | 作品被赞/被评论 → 打开作品并展开评论区；回复 → 再展开对应楼层并滚到那条回复（短暂高亮）；新增关注 → 打开对方主页 |
| 红点更新 | 每 30 秒轮询 + 切回标签页时立即补拉；进入某个 Tab 并加载完，就把该 Tab 清为已读 |
| 保留时长 | 通知保留 90 天，超期由定时任务清理 |
| 分页 | cursor 分页（游标编码见接口一节），每页 20 条，按最近更新时间倒序 |

## 后端设计

### 数据变更

新增 `db/init/005_m5_notification.sql`：

```sql
CREATE TABLE IF NOT EXISTS `notification` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '接收者',
    `type`        VARCHAR(20)  NOT NULL COMMENT 'LIKE_POST / LIKE_COMMENT / COMMENT_POST / REPLY_COMMENT / FOLLOW',
    `merge_key`   VARCHAR(64) NULL COMMENT '折叠键：按作品/评论折叠的类型填值并受唯一键约束；不折叠的类型（FOLLOW）为 NULL',
    `actor_id`    BIGINT UNSIGNED NOT NULL COMMENT '最近一个触发者',
    `actor_count` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '去重后的人数',
    `actor_ids`   VARCHAR(1200) NULL COMMENT '触发者 id 的 JSON 数组，最多 100 个，仅用于去重与折叠展示',
    `post_id`     BIGINT UNSIGNED NULL COMMENT '关联作品',
    `comment_id`  BIGINT UNSIGNED NULL COMMENT '关联评论',
    `root_id`     BIGINT UNSIGNED NULL COMMENT '所属顶层评论，回复通知用于定位楼层',
    `content`     VARCHAR(500) NULL COMMENT '评论/回复摘要',
    `read_at`     DATETIME NULL COMMENT '已读时间，NULL 表示未读',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_merge` (`user_id`, `merge_key`),
    KEY `idx_user_updated` (`user_id`, `updated_at`),
    KEY `idx_user_read` (`user_id`, `read_at`)
) ENGINE=InnoDB COMMENT='通知';
```

### 折叠规则怎么表达

最初的设计是直接用 `(user_id, type, post_id, comment_id)` 做唯一键，靠「唯一索引里 NULL 互不相等」来区分折叠与不折叠。**实测证明这是错的**：唯一索引中只要出现 NULL 列，整行的唯一性就不生效。`LIKE_POST` 的 `comment_id` 恒为 NULL，等于完全没有约束，同一条作品可以插出无数行，折叠根本不成立。

改用一个显式的折叠键 `merge_key`：

| 类型 | merge_key 取值 | 是否受唯一键约束 | 效果 |
|---|---|---|---|
| LIKE_POST | `post:{postId}` | 是 | 按作品折叠 |
| LIKE_COMMENT | `comment:{commentId}` | 是 | 按评论折叠 |
| COMMENT_POST | `cpost:{commentId}` | 是，但每条评论 id 不同 | 实际不折叠 |
| REPLY_COMMENT | `reply:{commentId}` | 是，同上 | 实际不折叠 |
| FOLLOW | NULL | 否 | 不折叠，每次关注都是新行 |

规则从"依赖 NULL 语义的隐式技巧"变成"一眼能看懂的显式取值"，同时四种折叠类型仍然共用同一个唯一键，不需要分支逻辑。

### 去重写入

折叠行需要「同一个人反复取消再点赞，人数不虚高」。写入走事务内行锁 + 读改写：

1. `SELECT ... FOR UPDATE` 锁住 `(user_id, merge_key)` 那一行
2. 若触发者 id 已在 `actor_ids` 中：只更新 `actor_id` / `updated_at` / `read_at = NULL`，人数不变
3. 若不在：追加进 `actor_ids`（上限 100），`actor_count + 1`，同时刷新上面三个字段
4. 行不存在时直接插入

超过 100 个不同点赞者后不再去重（列表已截断），这是刻意取舍——人数在几百以上时精确到个位没有意义。

### 事件

新增三个事件，与已有的 `CommentCreatedEvent` 一起供通知服务消费：

```java
public record PostLikedEvent(Long postId, Long likerId) {}
public record CommentLikedEvent(Long commentId, Long likerId) {}
public record UserFollowedEvent(Long followerId, Long followeeId) {}
```

- `LikeService.likePost` 只在 `addMember` 返回 true（点赞状态真的发生了变化）时发布 `PostLikedEvent`；`likeComment` 同理
- `FollowService.follow` 仅在首次关注成功时发布 `UserFollowedEvent`（重复关注不重复发）
- 取消点赞 / 取关不发布事件

### 通知生成规则

`NotificationService` 监听四个事件，按下表生成通知；**接收者等于触发者时直接跳过**：

| 事件 | 接收者 | 类型 | 关联字段 |
|---|---|---|---|
| `PostLikedEvent` | 作品作者 | LIKE_POST | post_id |
| `CommentLikedEvent` | 评论作者 | LIKE_COMMENT | comment_id |
| `CommentCreatedEvent`（root_id 为空） | 作品作者 | COMMENT_POST | post_id、comment_id、content |
| `CommentCreatedEvent`（root_id 非空） | `replyToUserId` | REPLY_COMMENT | post_id、comment_id、root_id、content |

`content` 存评论/回复正文的前 100 字，用于列表内直接预览。

### 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/notifications/unread` | 返回 `{ total, like, comment, follow }`，一次 `GROUP BY type` 查询算出，供红点与 Tab 徽标共用 |
| GET | `/api/notifications?category=like\|comment\|follow&cursor=&limit=` | 分类分页列表，按 `updated_at` 倒序 |
| POST | `/api/notifications/read?category=` | 把该分类下所有未读标记为已读 |

分页游标不能只用一个 id：折叠会刷新 `updated_at` 但不改 id，同一行会"跳"到列表前面，纯 id 游标必然漏数据或重复。
因此游标编码为 `<yyyy-MM-dd HH:mm:ss.SSS>_<id>`（毫秒精度的本地时间字符串，实现见 `NotificationService#encodeCursor`），翻页条件写成
`updated_at < ?1 OR (updated_at = ?1 AND id < ?2)`，排序为 `updated_at DESC, id DESC`。

「标记已读」必须显式把 `updated_at` 写回原值：表上的 `ON UPDATE CURRENT_TIMESTAMP` 会把刚读掉的行顶到列表最前面、时间也变成「刚刚」。

### VO

```java
NotificationVO {
    Long id; String type; String category; Boolean read;
    Actor actor;          // 最近触发者 {id, nickname, avatarUrl}
    Integer actorCount;
    List<Actor> actors;   // 最多 3 个，折叠展示用
    Long postId; Long commentId; Long rootId;
    String content;
    String postCoverUrl;  // 作品封面缩略图（作品被删除时为 null）
    LocalDateTime updatedAt;
}
```

列表查询后批量补全用户与作品信息，避免 N+1。

### 清理任务

`NotificationCleaner` 每天凌晨执行一次，删除 `updated_at` 早于 90 天的记录（沿用 `LikeCountFlusher` 的 `@Scheduled` 模式）。

## 前端设计

### 路由与入口

- 新增路由 `/notifications`（`requiresAuth`）
- 移动端 `BottomNav.vue`：把「消息」从 `todo()` 改成 `router.push('/notifications')`，并在图标右上角挂未读数字徽标
- PC 端：首页顶部迷你导航（`FeedView` 的 `.p-topbar`）与个人主页左侧导航（`ProfileView` 的 `.pc-side`）各接一个入口，未读数以胶囊形式跟在「消息」文字后面。
  （`components/pc/PcSideNav.vue` 是早期 PC 侧栏方案，当前没有任何页面渲染它，属于待清理的无用组件。）

### 状态

新增 `stores/notification.js`：

- `unread`：`{ total, like, comment, follow }`
- `refresh()`：拉一次未读数
- 登录后启动 30 秒轮询；监听 `visibilitychange`，页面重新可见时立即补拉
- 未读数为 0 时徽标不渲染；超过 99 显示「99+」

### 互动消息页

新增 `views/NotificationView.vue`：

- 顶部标题「消息」+ 三个 Tab（赞与收藏 / 评论 / 新增关注），Tab 上带各自未读数徽标
- Tab 内容为通知列表，cursor 分页，滚到底自动加载
- 每行结构：触发者头像 → 文案 → 时间；折叠的点赞通知用「彭于晏、拾光用户3533 等 5 人赞了你的作品」；评论/回复类在文案下方显示内容摘要；右侧显示作品封面缩略图
- 未读行左侧有一个小圆点标记
- 空状态分三种文案（对应三个 Tab）
- 切到某个 Tab 后：拉列表 → 拉完调 `POST /api/notifications/read?category=` → 刷新未读数

### 跳转逻辑

点击一行时按类型分流。**跳进的是单作品页 `/post/:id`（只放被点开的那一条，不支持上下滑），不是首页流**——
从通知点进去的场景是"我要看这条互动"，进首页流会顺手把整个信息流铺开，返回时也对不上来处。单作品页左上角有返回条，按返回就回到消息页：

| 类型 | 行为 |
|---|---|
| LIKE_POST | `router.push('/post/' + postId + '?comment=1')` |
| LIKE_COMMENT / COMMENT_POST / REPLY_COMMENT | `router.push('/post/' + postId + '?comment=1&rootId=&commentId=&highlight=1')`，其中 `rootId` 取「通知里的 rootId，顶层评论时回退成 commentId」——顶层评论自己就是所在楼层的根，这样三种类型能共用同一套定位逻辑 |
| FOLLOW | `router.push('/user/' + actorId)` |

`FeedView` 同时承担单作品页（`/post/:id`）：`feed.mode = 'single'` 时只加载 `fetchPostDetail` 的那一条，分页与"没有更多了"标记都不渲染，返回条走历史回退（没有历史就落到 `/notifications`）。
作品被删除时页面显示「作品不存在或已删除」，评论面板不会打开。

`FeedView` 需要支持两个新查询参数：

- `comment=1`：定位完成后自动打开评论面板
- `rootId` + `commentId`：评论面板打开后自动展开该楼层并滚到目标回复，短暂高亮（约 2 秒），其余行不受影响

定位参数保留在地址栏里不主动清除（和既有的 `?postId=` 一致，避免二次改写路由把信息流的播放状态搅乱）。

作品已删除时，跳转会落到「作品不存在或已删除」的既有处理，不额外兜底。

## 边界与决策

1. 通知是**历史记录**：取消点赞、取关、删除评论都不会回收已产生的通知。
2. 目标作品或评论被删除后，通知仍保留；点击时由既有逻辑提示「作品不存在或已删除」。
3. 折叠行的人数上限精确到 100，超过后不再去重。
4. 本期红点用轮询，里程碑 3 上 WebSocket 后轮询降级为兜底，接口不变。
5. `FeedView` 新增的 `comment` / `rootId` / `commentId` / `highlight` 参数只影响评论面板的打开与定位，不改动播放与列表逻辑。

## 验收标准

- 别人点赞你的作品：底部导航「消息」出现红点；进入「赞与收藏」看到折叠通知；红点与该 Tab 徽标清零。
- 同一个人取消再点赞：人数不增加。
- 多个人点赞同一作品：合并成一行，显示「A、B 等 N 人赞了你的作品」。
- 别人评论你的作品：出现在「评论」Tab，点进去打开作品并自动展开评论区。
- 别人回复你的评论：出现在「评论」Tab，点进去展开对应楼层并高亮那条回复。
- 别人关注你：出现在「新增关注」Tab，点进去打开对方主页。
- 你自己点赞/评论/关注自己：不产生任何通知。
- 未读数为 0 时红点消失；超过 99 显示「99+」。
- 切回浏览器标签页时红点立即刷新，不必等 30 秒。
