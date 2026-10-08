# 社交增强实施计划（收藏 / 可见性 / 分享卡片 / 富媒体输入）

设计文档：`docs/superpowers/specs/2026-09-18-social-enhancements-design.md`

## 步骤清单

### 步 0：互相关注文案
1. 后端 `UserPublicVO` 增加 `matched`（对方是否也关注了我），profile 查询顺带返回
2. 前端个人主页关注按钮：`matched && followedByMe` 时显示「互相关注」（PC 与移动端）

### 步 1：收藏
3. `db/init/008_m8_favorite.sql` 建 `post_favorite`
4. 后端：`PostFavorite` 实体 + Mapper、`FavoriteService`（收藏/取消/分页/批量状态与计数）、`PostController` 两个接口
5. 后端：`PostVO` 加 `favorited` / `favoriteCount`，作品流、单作品、主页作品列表批量填充
6. 后端：`GET /api/user/{id}/favorites` 分页接口（步 3 之前先按公开处理）
7. 前端：互动栏（移动端 `FeedItem.vue` + PC `PcRightPanel.vue`）加星标按钮与计数；`FeedView` 接上收藏切换
8. 前端：个人主页「收藏」tab 接通真实数据（PC + 移动端）
9. 验证：接口冒烟（收藏/取消幂等、计数、私密作品拦截、分页）+ 无头 Edge（点星标点亮与计数、主页收藏 tab 展示、刷新后状态保持）

### 步 2：富媒体输入（评论 + 私信）
10. 数据：`comment.image_urls`；`private_message.type / image_urls / post_id`（`db/init/010_m10_message_media.sql`）
11. 后端：评论创建支持图片（最多 3 张，文字与图片至少一个非空），评论 VO 返回图片
12. 后端：私信发送支持 `TEXT / IMAGE / POST_CARD`，会话摘要按类型显示（`[图片]` / `[作品] 标题`）
13. 前端：公共组件 `EmojiPanel.vue`、`ImageViewer.vue`（点开大图，评论/私信共用）
14. 前端：评论输入框改造（表情、+ 选图、缩略图条、按内容切换 + / 发送）
15. 前端：聊天输入框同样改造，聊天页渲染图片消息宫格与卡片消息，会话列表摘要适配
16. 验证：接口冒烟（评论带图、纯图评论、超 3 张拒绝、私信三类消息、非互关限制仍生效）+ 无头 Edge（表情插入、选图发送、图片大图预览、卡片消息渲染）

### 步 3：账号设置页 + 可见性
17. `db/init/009_m9_privacy.sql` 建 `user_privacy`
18. 后端：`UserPrivacy` 实体 + Mapper、`GET/PUT /api/user/privacy`
19. 后端：`UserPublicVO.viewerCanSee`（四项布尔，好友 = 互关）
20. 后端：`/likes`、`/favorites`、`/followers`、`/following` 四个列表接口加可见性校验（无权访问返回业务错误 + 文案）
21. 前端：`/settings` 页面（资料 / 隐私四项 / 账号 / 关于），路由与入口（移动端抽屉、PC 自己主页按钮）
22. 前端：他人主页按 `viewerCanSee` 隐藏不可见分区（tab、可点击的关注/粉丝数），受限列表页显示提示空状态
23. 验证：接口冒烟（三档 × 四种列表的权限矩阵、默认公开、互关好友可见）+ 无头 Edge（设置页保存后他人主页即时生效、提示文案、互相关注文案）

### 步 4：分享面板 + 作品卡片
24. 前端：分享面板组件（作品缩略 + 最近聊过好友横滑列表 + 复制链接 + 系统分享），移动端底部升起、PC 居中弹层
25. 前端：修掉 `FeedView.onShare` 复制首页链接的 bug，改为单作品链接；分享给好友时调用私信接口发 `POST_CARD`
26. 前端：聊天页作品卡片渲染（封面 + 标题 + 作者，点击进单作品页）
27. 验证：接口冒烟（卡片消息落在会话、摘要为 `[作品]`、非互关限制仍生效）+ 无头 Edge（分享面板选好友 → 对方会话出现卡片 → 点击卡片进单作品页 → 返回回到聊天页；复制链接为单作品地址）

### 文档与收尾
28. `PROGRESS.md` 追加本组功能；设计文档与计划同步更新

## 验证方式

- 后端：`mvnw -DskipTests compile` + Python 接口冒烟（复用 `.devtools/logs/` 下既有脚本模式，token 取 t347/t351.txt）
- 前端：`npm run build` + 无头 Edge CDP 脚本（移动端 430×900、PC 1400×900）
- 每步完成后向用户汇报改动与验证结果
