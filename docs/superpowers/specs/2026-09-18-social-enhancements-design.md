# 社交增强设计（收藏 / 可见性设置 / 分享卡片 / 富媒体输入）

日期：2026-09-18　状态：已与用户确认，待实施

## 目标

补齐四块缺失能力，让"收藏、分享、评论发图发表情"成为完整闭环：

1. **收藏**：作品可收藏，个人主页「收藏」tab 接通真实数据
2. **可见性设置**：个人主页「账号设置」做成真实页面，可分别设置 *点赞 / 收藏 / 粉丝 / 关注* 列表的可见性
3. **分享**：分享给站内好友走"作品卡片私信"，站外走复制链接
4. **富媒体输入**：评论与私信都支持发图片、插入表情，发送键按"有无内容"在「+」与「发送」之间切换

## 已确认的决策

| 决策点 | 结论 |
|---|---|
| 好友定义 | **互相关注 = 好友**（不新增好友关系表，用现有 follow 表判定） |
| 可见性档位 | **公开 / 好友 / 私密** 三档 |
| 默认可见性 | **四项全部默认「公开」**（含收藏与喜欢） |
| 互关时的关注按钮 | 对方主页显示「**互相关注**」而不是「已关注」 |
| 图片条数 | 评论最多 **3 张**；私信最多 **9 张** |
| 表情 | 内置 emoji 面板（系统 emoji 字符，不依赖图片资源），评论与私信共用同一个组件 |
| 分享卡片 | 卡片消息**同样受"非互关先发方最多 1 条"限制**（防刷屏，分享面板只列最近聊过的人，实际基本不受影响） |
| 收藏通知 | **不发**（弱互动，点赞才发） |
| 评论通知带图 | 通知摘要只带文字；纯图片评论摘要显示「[图片]」 |

## 一、收藏

### 数据（`db/init/008_m8_favorite.sql`）

```sql
CREATE TABLE IF NOT EXISTS `post_favorite` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `post_id`    BIGINT UNSIGNED NOT NULL,
    `user_id`    BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
    KEY `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB COMMENT='作品收藏';
```

- **不加 `favorite_count` 冗余列**：和评论数一样，每页一次 `GROUP BY post_id` 统计，避免计数漂移（点赞那套 ±1 维护是早期写法，不再扩散）
- 收藏是"自己的私有动作"，不发布事件、不写通知

### 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/posts/{id}/favorite` | 收藏，返回 `{favorited, favoriteCount}`；重复收藏幂等 |
| DELETE | `/api/posts/{id}/favorite` | 取消收藏，同样幂等 |
| GET | `/api/user/{id}/favorites?cursor=&limit=` | 收藏列表（按收藏时间倒序），游标复用 `millis_id` 风格 |

- 作品可见性沿用现有规则：私密作品只有作者能收藏/查看
- `PostVO` 增加 `favorited`、`favoriteCount`，在作品流/单作品/主页作品列表里一次性批量填充（与 `liked` 同一批查询）

### 前端

- 移动端右侧互动栏（`FeedItem.vue`）与 PC 右面板（`PcRightPanel.vue`）在「分享」上方加星标按钮，点亮 + 计数
- 个人主页「收藏」tab 接通真实数据（网格 + 分页），空状态保留
- 收藏状态在作品流、单作品页、主页作品流之间保持一致（都读同一份 VO 字段）

## 二、可见性设置（账号设置页）

### 数据（`db/init/009_m9_privacy.sql`）

```sql
CREATE TABLE IF NOT EXISTS `user_privacy` (
    `user_id`              BIGINT UNSIGNED NOT NULL,
    `like_visibility`      VARCHAR(10) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC / FRIENDS / PRIVATE',
    `favorite_visibility`  VARCHAR(10) NOT NULL DEFAULT 'PUBLIC',
    `follower_visibility`  VARCHAR(10) NOT NULL DEFAULT 'PUBLIC',
    `following_visibility` VARCHAR(10) NOT NULL DEFAULT 'PUBLIC',
    `updated_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB COMMENT='用户列表可见性设置';
```

- **没有记录 = 全公开**，不强制为每个用户建行（新注册用户零成本）

### 接口

- `GET /api/user/privacy`（自己）：返回四项设置
- `PUT /api/user/privacy`（自己）：整份覆盖保存
- `GET /api/user/{id}` 的 `UserPublicVO` 增加 `viewerCanSee`：`{like, favorite, follower, following}` 布尔（服务端算好，前端只用来显示/隐藏 tab 与入口）
- 受限的列表接口（`/likes`、`/favorites`、`/followers`、`/following`）在无权访问时返回业务错误 `403 + 文案`（如「TA 的收藏仅自己可见」）

### 前端

- 新页面 `/settings`（账号设置）：
  - 资料：编辑资料
  - 隐私：四项可见性（公开 / 好友 / 私密 分段控件）
  - 账号：退出登录
  - 关于：版本号
- 入口：移动端个人主页右上角抽屉「账号设置」→ `/settings`；PC 端自己主页按钮区加「账号设置」
- 他人主页：不可见的分区不显示 tab / 不显示可点击的关注·粉丝数；直接访问受限列表页时显示"仅好友可见 / 仅自己可见"空状态
- **互相关注**：`UserPublicVO` 增加 `matched`（对方也关注了我），对方主页关注按钮显示「互相关注」

## 三、分享（作品卡片 + 复制链接）

### 数据（并入 `010_m10_message_media.sql`）

`private_message` 增加：

| 列 | 说明 |
|---|---|
| `type` | `TEXT` / `IMAGE` / `POST_CARD`，默认 `TEXT` |
| `image_urls` | JSON 数组（图片消息 1~9 个 objectName） |
| `post_id` | 卡片消息指向的作品 |

### 接口

- `POST /api/messages` 扩展：`type` + `imageUrls` + `postId`
  - `TEXT`：需 content
  - `IMAGE`：需 1~9 张图
  - `POST_CARD`：需 postId，作品必须存在且对发送者可见
  - 三种类型同样走 `sendState()` 限制
- 会话列表摘要：文本 / `[图片]` / `[作品] 标题`
- 不需要新接口：分享面板的好友列表直接复用 `GET /api/conversations`

### 前端

- 分享面板（移动端底部升起 / PC 居中弹层）：
  - 顶部作品缩略（封面 + 标题）
  - 「最近聊过」好友头像横向列表（取会话前 10）
  - 底部操作：复制链接（`/post/{id}` 完整地址）、系统分享（手机支持时）、取消
  - 没有会话时提示"还没有聊过的好友，去主页点私信"
- **修掉现有 bug**：`FeedView.onShare` 现在复制的是 `首页链接`，改为单作品链接
- 聊天页渲染三类消息：文本气泡 / 图片宫格（点开大图）/ 作品卡片（封面 + 标题 + 作者，点击进单作品页）
- 单作品页返回按历史回退，所以"聊天页 → 卡片 → 返回"回到聊天页

## 四、富媒体输入（评论 + 私信）

### 评论图片

- `comment` 表加 `image_urls VARCHAR(1200) NULL`（JSON 数组，最多 3 个）
- 创建评论：content 与 images 至少一个非空；纯图片评论允许
- 评论列表渲染缩略图宫格，点开大图（新增公共 `ImageViewer.vue`，预览头像/评论图/私信图共用）

### 输入框统一形态（评论与聊天共用同一套交互）

```
[😊]  [ 输入框 ]  [ + ]        ← 无内容时：右侧是「+」，点开选图片
[😊]  [ 输入框 ]  [ 发送 ]      ← 有文字或已选图片时：变回「发送」
已选图片：输入框上方出现缩略图条，可单张删除
```

- 新增公共组件：`EmojiPanel.vue`（约 80 个常用 emoji，分组展示，点选插入光标处）、`ImagePickerButton`、`ImagePreviewStrip`、`ImageViewer.vue`
- 上传复用 `/api/upload/presign`（type=IMAGE），直传 MinIO，展示用 `/api/media/{objectName}`

## 实施顺序

| 步 | 内容 | 依赖 |
|---|---|---|
| 0 | 互相关注文案（`matched` 字段 + 按钮文案） | 无 |
| 1 | 收藏：建表、接口、互动栏星标、主页收藏 tab | 无 |
| 2 | 输入框改造：+ / 发送切换、图片（评论 3 / 私信 9）、表情面板 | 无 |
| 3 | 账号设置页 + 四项可见性 + 列表页受限提示 | 步 1（收藏列表） |
| 4 | 分享面板 + 作品卡片私信 + 修掉分享链接 bug | 步 2（聊天页扩展） |

## 不做（YAGNI）

- 分享到微信/QQ 等第三方平台的原生 SDK（需要备案域名与平台资质，站外统一用复制链接）
- 生成分享卡片图（canvas 出图）
- 好友分组、特别关注、拉黑
- 表情收藏/最近使用
