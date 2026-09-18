# 拾光（Shiguang）开发进度

> 记录日期：2026-09-01 ｜ 分支：dev ｜ 仓库：github.com/SuperYe12-21/Shiguang

## 技术栈
- 后端：Spring Boot 3 + Maven + JDK 21 + MySQL + Redis + RabbitMQ + MinIO
- 前端：Vue 3 + Vite + Pinia + Element Plus（手机/PC 自适应）
- 主题：米白底 + 珊瑚红暖色，设计令牌见 `frontend/src/styles/tokens.css`

## 已完成
1. 账号：手机号 + 验证码登录（开发环境固定 123456）、JWT、资料编辑、关注/粉丝
2. 内容：图文/视频发布（MinIO 预签名直传）、多图滑动、首页一屏一卡（移动端滑动 / PC 滚轮）
3. 互动：点赞、评论、评论点赞/删除、分享入口
4. 移动端个人主页（方案 D）：作品/点赞/收藏 Tab、网格、右上抽屉（编辑资料/分享主页/账号设置）、备注、返回链路
5. 点赞流：主页点赞 Tab 点作品 → 进入该用户点赞流（`/feed?postId=&likesOf=`），可上下滑动
6. 用户作品流：主页点作品 → 该用户作品流（`/feed?postId=&scopeUserId=`），返回逐步回退
7. 移动端体验：`visualViewport` 驱动卡片高度（解决真机地址栏导致内容偏下）、媒体偏上布局、自动播放优化（预加载 + loadeddata 自动播 + 静音兜底）、暂停时可点评论、评论区隐藏播放遮罩、视频内联播放兼容属性（webkit/X5）
8. 头像修复：评论/关注列表头像转预签名 URL，前端加载失败回退字母头像

## 遗留问题
- 部分国产手机浏览器会接管视频弹系统播放器：兼容属性已加，仍出现则需换 Chrome/Edge 验证（内核限制，无法代码绕过）

## 2026-09-18 返回首页瞬间定位（不再从第一条滑过去）
- 问题：从个人主页 / 消息页返回首页 `/feed` 时，PC 端 `.p-stack` 的 `transition: transform 0.45s` 会把「恢复到刚才那条」播成一条从第一条滑过去的动画；移动端 `scroll-snap-type: y mandatory` 的吸附动画也有同样的观感
- 修复：`FeedView.vue` 增加 `feedReady` 开机定位开关（`onMounted` 的 `initFeed` 前后、`refreshHomeFeed`、`route.fullPath` watcher 三处维护），定位阶段给容器挂 `booting` 类——移动端 `.m-scroll.booting { scroll-snap-type: none }`、PC 端 `.p-stack.booting { transition: none }`，定位完成即恢复
- 验证（无头 Edge 430×900 / 1400×900）：六条返回路径全部一帧到位——移动端（浏览器返回 / 点底部首页按钮 / 点作者进他人主页再返回）`scrollTop` 采样轨迹 `[1800]` 即第 3 条；PC（浏览器返回 / 点左侧首页按钮 / 点作者进他人主页再返回）`translateY` 采样轨迹 `[-1610]` 即第 3 条，返回前后作品特征一致
- 回归：PC 正常翻页仍有 26 帧过渡动画、`transition-duration` 恢复 `0.45s`、移动端 `scroll-snap-type` 恢复 `y mandatory`；控制台无错误

## 下一步计划
- **PC 端个人主页重设计**：候选方案预览页 `.devtools/profile-preview/preview-pc-a~d.html`（本地 5858 预览服务器），待确认方案后落地实现

## 2026-09-02 更新
- 新增 9：PC 端个人主页（方案 A 经典分栏落地）：浅色主题 + 左侧固定导航 + 渐变横幅 + 返回按钮，作品/点赞/收藏 Tab、网格角标、点击进入对应作品流/点赞流，他人主页关注/分享/设置备注
- 新增 10：关注/粉丝列表页 `FollowListView.vue`（浅色 PC / 深色移动端自动切换），主页点击关注/粉丝人数跳转对应列表，列表可关注/取关、点头像进入对方主页
- 新增 11：后端 `UserPublicVO.followedByMe`（关注状态）供列表按钮态使用

## 下一步计划
- PC 端个人主页：用户确认返回按钮与关注/粉丝列表效果后再继续打磨/新功能

## 2026-09-02 体验优化（二）
- 首页流位置记忆：从首页进入他人/我的主页再返回时，自动恢复到离开前正在看的视频/图文，不再跳回第一条（PC/移动端均生效，`FeedView.vue` 用 sessionStorage 记录离开前作品）

## 2026-09-02 体验优化（三）
- 移动端返回首页恢复位置改为瞬间定位：移除 .m-scroll 的 scroll-behavior:smooth，不再从第一条平滑划过中间视频

## 2026-09-02 体验优化（四）
- 视频进度条：PC/移动端视频底部新增可拖动进度条（珊瑚红进度 + 拖拽 seek，移动端评论打开时隐藏）
- 断点续播：离开首页进入个人主页时记录当前视频播放秒数，返回后从离开位置继续播放，不再从头开始

## 2026-09-02 体验优化（五）
- 修复拖动进度条残留播放遮罩：拖动触发 pause 时隐藏中央 ▶ 图标（PC/移动端），松手恢复播放后图标状态同步（PC 补上 play 事件监听），不再出现“视频已恢复但图标还在”
- 断点续播改为尽早定位：在 loadedmetadata（首帧解码前）即执行 seek，且 readyState 未就绪时预设起点、就绪后再确认，返回首页不再先闪第 0 帧再跳到播放位置，而是直接显示续播画面

## 2026-09-02 体验优化（六）
- 拖动进度条时新增悬浮时间提示：大字号“分:秒”格式气泡（如 0:27 / 0:45）固定在进度条上方居中显示（不随拖动位置移动），PC/移动端均生效
- 拖动期间暂时隐藏底部文案与标题（渐隐过渡），松手恢复后文案淡回，避免与时间提示互相干扰

## 2026-09-02 体验优化（七）
- 去掉返回续播时的封面闪现：续播视频在定位完成前不显示封面占位（本实例内关闭 poster），定位完成后直接显示对应进度的画面
- 新增播放就绪门控：返回首页时先完成“断点定位”再起播，杜绝先播 0 秒开头再跳转的闪帧（FeedView feedReady + 组件内 seekPending 等待）

## 2026-09-02 体验优化（八）
- 离场帧快照：离开首页前用 canvas 截取当前视频画面（约 34KB JPEG，0.25 缩放），随断点记录存入 sessionStorage
- 返回续播时先用该快照铺底（无黑屏等待），视频 seek 完成后无缝切换回真实画面；视频元素增加 crossorigin=anonymous，配合媒体服务 CORS（MinIO 已配置）保证 canvas 可取帧
- 部署注意：媒体服务（MinIO/OSS）需返回 CORS 头，否则快照功能自动降级为原黑屏等待，不影响播放

## 2026-09-02 体验优化（九）
- 移动端返回续播不再闪现 ▶ 播放图标：续播窗口内（resumeActive）隐藏播放遮罩，且遮罩只在“当前激活且确实暂停”时显示；播放开始或 2.5s 兜底后自动恢复，手动暂停的 ▶ 指示不受影响

## 2026-09-02 体验优化（十）
- 首页“首页”按钮支持点击刷新：PC 端顶栏与移动端底部导航在已处于首页流时点击“首页”，重新拉取最新作品并回到第一屏（刷新期间显示加载骨架，完成后轻提示“首页已刷新”）
- 在他人作品流 / 点赞流（`/feed?userId=`、`/feed?likesOf=`）中点击“首页”仍按原逻辑先回到首页，不影响“从主页返回恢复观看位置”的续播体验

## 2026-09-02 体验优化（十一）
- 用户主页头像查看大图：PC 端与移动端在个人主页（自己或他人）点击头像，弹出全屏大图预览（深色毛玻璃背景 + 居中大图 + 右上角关闭按钮），点遮罩、关闭按钮或按 Esc 均可关闭；未设置头像（字母占位）时点击不弹窗

## 2026-09-02 体验优化（十二）
- 修复移动端“分享主页”不可用：手机通过局域网 IP（http，非安全上下文）访问时系统分享与剪贴板 API 均不可用，原先直接报“分享失败”
- 分享改为三级降级：优先系统分享面板 → 剪贴板/隐藏输入框 execCommand 复制 → 仍失败则弹窗展示链接支持长按手动复制；复制成功轻提示“主页链接已复制”

## 2026-09-10 首页播放列表优化（已看沉底 + 轮换重播）
- 登录用户的有效观看（累计满 3 秒）记入 Redis ZSET `feed:seen:{userId}`（member=postId，score=最后观看时间，保留 90 天）
- 首页两段式：先给没看过的作品；全部看完后按“最久没看”顺序轮换重播（游标 `seen_{offset}`，可回绕），未登录仍为时间倒序
- 前端 3 秒观看计时上报（`useSeenReport.js`，同一作品 60 秒内不重复上报），PC / 移动端均接入；轮换阶段允许同一作品重复出现，列表 key 改为「ID-索引」
- 返回首页定位支持重复作品：取离上次停留位置最近的一次，不再跳回最早位置

## 2026-09-10 发布后跳转
- 发布成功（含视频转码完成）后不再回首页信息流，改为进入“自己主页的作品流”并停在新作品上（`/feed?postId=&userId=&from=publish`）
- 发布页用 `replace` 移出历史，作品流点返回直接回个人主页；转码超时未发布成功时仍回首页
- 离开首页去任何页面（发布页/个人主页等）都会记录当前位置，返回时恢复（含播放进度）；目标不在第一页时自动向后加载分页定位

## 2026-09-10 作品管理（仅自己可见 / 编辑文案 / 删除）
- 后端：`post` 表新增 `visibility`（PUBLIC / PRIVATE，`db/init/003_m3_post_visibility.sql`），新增 `PUT /api/posts/{id}/visibility`、`PUT /api/posts/{id}`（编辑文案），均为作者权限；删除复用 `DELETE /api/posts/{id}`
- 可见性过滤：首页流（含轮换重播）、他人主页作品、他人点赞列表都排除 PRIVATE；私密作品详情对非作者返回“作品不存在”，点赞/评论接口同样拦截；作者本人作品列表保留并返回 `visibility`
- 前端：作品播放页右侧互动栏新增「⋯」（仅自己的作品），弹出“作品设置”面板（移动端底部升起 / PC 贴互动栏浮出）：仅自己可见开关、编辑文案（标题 + 简介）、删除作品（二次确认）
- 个人主页网格私密作品显示锁角标

## 2026-09-18 二级评论（消息系统里程碑 1 / 3）
设计文档：`docs/superpowers/specs/2026-09-18-nested-comments-design.md`；实施计划：`docs/superpowers/plans/2026-09-18-nested-comments-plan.md`

- 数据：`comment` 新增 `parent_id` / `root_id` / `reply_to_user_id` 三列与 `idx_post_root_id`、`idx_root_id` 两个索引（`db/init/004_m4_comment_reply.sql`），两级固化，存量数据无需迁移
- 后端：顶层评论改为时间倒序分页（`id < cursor`）；新增 `GET /api/comments/{id}/replies`（回复分页，传顶层或任一回复 id 都能定位到楼层）与 `POST /api/comments/{id}/replies`（发回复，服务端推导 rootId 与 replyToUserId）
- 回复数不维护冗余字段，改为每页一次按 `root_id IN (...)` 的 `GROUP BY` 统计，删除与级联都不会造成计数漂移
- 删除权限扩展：评论作者本人或作品作者；删顶层评论时连同其下全部回复一起删，`comment_count` 按实际删除数扣减
- `CommentCreatedEvent` 扩展为 `(postId, commentId, authorUserId, parentId, rootId, replyToUserId)`，里程碑 2 的通知中心直接消费，评论侧无需返工
- 前端 `CommentPanel.vue` 重构：回复默认折叠，显示「查看 N 条回复」，点开每次加载 10 条正序回复并可继续翻页；点「回复」进入回复模式（输入框上方出现可取消的「回复 @某某」提示条）；回复「回复」时正文前显示「回复 @某某：」；作品作者的评论和回复带「作者」小标签；删除按钮改由后端 `canDelete` 控制
- 验证：接口冒烟（倒序、楼层归属、不产生第三层、replyToUser 只在回复回复时返回、越权删除 403、级联删除计数归零）+ 无头 Edge 端到端（移动端 430×900 与 PC 1400×900 各跑一遍：折叠/展开/@前缀/作者标签/回复模式/界面发送回复，控制台无错误）

## 2026-09-18 通知中心（消息系统里程碑 2 / 3）
设计文档：`docs/superpowers/specs/2026-09-18-notification-center-design.md`；实施计划：`docs/superpowers/plans/2026-09-18-notification-center-plan.md`

- 数据：新增 `notification` 表（`db/init/005_m5_notification.sql`），用显式折叠键 `merge_key` + 唯一键 `uk_merge(user_id, merge_key)` 实现点赞折叠——**不能用 `(user_id, type, post_id, comment_id)` 做唯一键**，唯一索引里出现 NULL 列会让整行唯一性失效，`LIKE_POST` 的 `comment_id` 恒为 NULL 等于没有约束
- 事件：新增 `PostLikedEvent` / `CommentLikedEvent` / `UserFollowedEvent`，只在状态真的变化时发布（重复点赞、重复关注不发）
- 写入：`NotificationService` 监听事件，接收者等于触发者直接跳过；折叠行在事务内 `SELECT ... FOR UPDATE` 锁行后读改写，同一人重复点赞人数不涨（`actor_ids` 去重，上限 100），新触发者排到最前
- 查询：`GET /api/notifications/unread`（一次 GROUP BY 算出总数与三个分类数）、`GET /api/notifications?category=&cursor=&limit=`（游标 `updated_at_id`，毫秒精度）、`POST /api/notifications/read?category=`
- 接口层注意：`markRead` 必须显式 `updated_at = updated_at`，否则表上的 `ON UPDATE CURRENT_TIMESTAMP` 会把刚读掉的行顶到列表最前面、时间变成「刚刚」
- 清理：`NotificationCleaner` 每天凌晨删 90 天前的记录
- 未读数：`stores/notification.js` 每 30 秒轮询 + 切回标签页立即补拉，登出停止并清空
- 入口：移动端底部导航「消息」+ 左上角浮标红点（超过 99 显示 99+）；PC 端首页顶部迷你导航与个人主页左侧导航的「消息」右侧挂胶囊红点；新增路由 `/notifications`
- 消息页 `NotificationView.vue`：三个 Tab（赞与收藏 / 评论 / 新增关注，各带分类徽标）、点赞折叠行「A、B、C 等 N 人赞了你的作品」、评论与回复行显示内容摘要、右侧作品封面缩略图（图文回退第一张图）、未读行左侧小圆点、滚到底分页、三个空状态；进入某个 Tab 拉完列表就标记该分类已读并刷新红点
- 点击跳转：点赞/评论类进**单作品页**并自动打开评论面板；回复与评论点赞再带上 `rootId`/`commentId` 展开楼层、滚到那一条并高亮 2 秒；关注类进对方主页。顶层评论的 `rootId` 回退成 `commentId`，三类通知共用一套定位逻辑（`CommentPanel` 新增 `focusRootId` / `focusCommentId`，最多翻 5 页找不到就静默放弃）
- 单作品页 `/post/:id`：只放被点开的那一条（`feed.mode = 'single'`，复用 `FeedView` + `FeedItem` / `PcFeedCard`），不支持上下滑、不渲染"没有更多了"；左上角返回条按历史回退，没历史就落到消息页，所以「消息 → 作品 → 返回」能回到原消息页。作品已删除时显示「作品不存在或已删除」且不打开评论面板；首页流与主页作品流不受影响（`/feed` 原有 `?postId=` 机制保留）
- 返回控件压在评论面板遮罩之上（`z-index: 2200` > 遮罩 `2000`）：开着评论点返回一次就能返回，不再出现「第一下只关评论、第二下才返回」；点遮罩本身仍然只关评论
- 修复组件实例复用带来的状态残留：`/feed` 与 `/post/:id`、`/me` 与 `/user/:id`、粉丝与关注列表都用同一个组件，vue-router 在 path 变化但组件相同时会**复用实例**，`onMounted` 不再执行，页面级临时状态（评论面板、当前作品下标）会被带到下一个页面——表现为「从单作品页点首页回到信息流，评论面板还开着」。`App.vue` 的 router-view 改为 `:key="route.path"`，path 变化即换新实例；只有 query 变化（`/feed?postId=`、`/notifications?category=`）仍复用实例，交给各自的 watcher 处理，行为不变
- 验证：接口冒烟（五种通知生成、折叠去重、分类已读、游标分页、自己操作不通知自己、90 天清理）+ 无头 Edge 端到端（PC 1400×900 与移动端 430×900：红点数值与定位、三个 Tab 渲染、「等 N 人」文案、缩略图、未读圆点、高亮持续 2004ms、切回标签页立即补拉、单作品页只有 1 张卡片且返回回到消息页、作品不存在时的兜底、首页流滚轮翻页回归、控制台无错误）

## 下一步计划
- 消息系统里程碑 3：私信 IM（互关可聊 / 非互关限一条、消息页分区、WebSocket 推送，并把红点轮询降级为兜底）
