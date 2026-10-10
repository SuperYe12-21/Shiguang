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

## 2026-09-18 私信 IM（消息系统里程碑 3 / 3）
设计文档：`docs/superpowers/specs/2026-09-18-private-message-design.md`；实施计划：`docs/superpowers/plans/2026-09-18-private-message-plan.md`

- 数据：新增 `conversation`（一对用户一条，`uk_pair` 唯一键保证不会出现重复会话）与 `private_message`（`db/init/006_m6_message.sql`）
- 会话双方统一按 `min(id)/max(id)` 落到 `user_a_id` / `user_b_id`，查会话永远是一次等值查询；「已读」不做每人一行回执，只在会话上记一个游标（`a_last_read_id` / `b_last_read_id`），未读数用 `id > 游标` 统计
- 发送规则（`sendState()` 服务端统一判定，前端只展示结果）：互相关注 → 自由聊；非互关 → 先发的一方最多 1 条（提示「互相关注后才能连续发消息」），对方回复过就解锁（提示「对方回复后才能继续聊天」）
- 接口：`GET /api/conversations`（游标 `millis_id`）、`GET /api/conversations/{peerId}/messages`（顺带返回 `canSend` / `hint`）、`POST /api/messages`、`POST /api/conversations/{peerId}/read`、`GET /api/messages/unread`
- WebSocket：`/ws` 握手用 `?token=` 校验 JWT（`SecurityConfig` 只放行握手路径 + 静态资源），事务提交后（AFTER_COMMIT）给收发双方推 `message` 事件、给接收方推 `unread` 事件；`SocketSessions` 按 userId 维护连接，带 ping/pong 心跳
- 前端 `stores/message.js`：单例长连接（3 秒重连 + 30 秒心跳）+ 30 秒轮询兜底 + 切回标签页立即补拉；`subscribe()` 给页面订阅新消息，`App.vue` 随登录态 start/stop（登出断开并清零红点）
- 页面：`/messages` 会话列表（头像、昵称、最后一条消息带「我：」前缀、相对时间、未读徽标、滚到底分页）、`/chat/:userId` 聊天页（气泡左右分向、间隔超过 5 分钟插时间分隔、滚到顶部加载更早、发送后自动滚到底、非互关时输入框禁用并显示原因）
- 页面结构（按「私信是消息页主体」的定位）：`/messages` 打开就是私信——上方横排三个互动消息入口（赞与收藏 / 评论 / 新增关注，各自带分类未读角标），下方「私信」分区直接列出会话列表；互动入口点击跳 `/notifications`（标题「互动消息」，三个分类 Tab，默认选中被点的分类），私信不做页签按钮
- 入口：底部导航 / PC 顶栏 / PC 个人主页侧栏的「消息」统一指向 `/messages`，红点为「通知未读 + 私信未读」合并；他人主页三处（PC 操作区、移动端按钮组、移动端右上角抽屉）新增「私信」
- 验证：接口冒烟（非互关第 1 条成功 / 第 2 条被拒 / 对方回复后解锁 / 已读推进 / 未读计数 / 分类已读互不影响 / 边界校验）+ 无头 Edge 端到端 45 项断言（移动端 430×900 与 PC 1400×900：从主页进聊天、非互关限一条、对方回复无刷新实时到达、离开聊天页红点实时 +1、会话列表跳转与已读清零、返回路径、PC 宽度约束、无控制台错误）+ 结构调整后复验 25 项（消息页三入口与分类角标、私信默认可见、点入口按分类跳转并选中、看过的分类角标消失、底部导航/PC 顶栏入口、聊天页返回回到消息页）
- 顺带补了 `frontend/public/favicon.svg`（此前每个页面都会报一次 favicon.ico 404）

## 2026-09-18 修复：关注通知只提示最初的一次
- 问题：`FOLLOW` 通知的 `merge_key` 为 NULL（当初的决策是"关注不折叠"）。`FollowService` 虽然"重复关注不发事件"，但**取关再关注是真实的状态变化**，每来回一次就给对方新增一条「XX 关注了你」——实测同一个账号堆到 6 条，最新一条还带红点，等于反复取关/关注就能刷屏对方通知列表
- 修复：关注通知改走 `follow:{actorId}` 去重（`NotificationService.writeFollowOnce`），已存在就直接返回：不新建、不刷新 `updated_at`、不把旧通知重新置为未读。点赞/评论/回复的折叠逻辑不动
- 数据迁移：`db/init/007_m7_follow_notice_dedupe.sql`——同组 `(user_id, actor_id)` 只保留最早一条，组内曾有未读则把保留行置为未读（"该提示的那一次"不丢），并给保留行补上折叠键。现网数据：347 的 6 条关注通知收敛为 1 条
- 验证：接口层（351 反复取关/关注 3 轮 → 347 关注通知仍为 1 条、未读不增加）+ 无头 Edge（关注列表只有 1 条且文案正确、进列表后自动已读、之后反复操作消息页「新增关注」入口红点始终为 0、无控制台错误）

## 下一步计划
- 部署上线（M7）：服务器 / 域名 / Nginx（需同时转发 `/api`、`/ws` 与前端静态资源）/ HTTPS / 生产环境配置
- 上线前的体积优化：主包 1.1MB（gzip 367KB）超过 Vite 500KB 告警线，可按路由拆分 Element Plus 与首屏组件

## 2026-09-18 社交增强（1/5）：互相关注文案 + 作品收藏
设计文档：`docs/superpowers/specs/2026-09-18-social-enhancements-design.md`；实施计划：`docs/superpowers/plans/2026-09-18-social-enhancements-plan.md`

### 步 0：互相关注
- 「好友 = 互相关注」，不新增好友关系表：`UserProfileVO` / `UserPublicVO` / `FollowVO` 三处都加上 `matched`（对方是否也关注了我）
- 前端文案 `profile.followedByMe && profile.matched ? '互相关注' : '已关注'`，个人主页 PC / 移动端三处按钮 + 粉丝·关注列表按钮统一；关注/取关后立即按接口返回的 `matched` 更新，无需刷新
- 验证：接口（互关 → `matched=true`，取关后 `matched=false`，粉丝/关注列表逐行带 `matched`）+ 无头 Edge（互关时显示「互相关注」，取关变「+ 关注」，再关注回到「互相关注」）3 项全过

### 步 1：收藏
- 数据：`db/init/008_m8_favorite.sql` 新建 `post_favorite`（`uk_post_user` 唯一键保证幂等），**不加 `favorite_count` 冗余列**，计数按 `post_id` 分组实时统计，避免计数漂移
- 接口：`POST/DELETE /api/posts/{id}/favorite`（幂等，返回 `{favorited, favoriteCount}`）、`GET /api/user/{id}/favorites`（按收藏时间倒序游标分页）；`PostVO` 增加 `favorited` / `favoriteCount`，信息流与单作品页批量填充
- 私有作品只有作者能收藏（复用 `likeService.requireAccessiblePost`，他人收藏私密作品返回「作品不存在或已删除」）；作品删除时 `favoriteService.cleanupPost` 一并清理收藏
- 前端：移动端互动栏与 PC 互动栏在「分享」左侧新增收藏按钮（星标，点亮为琥珀色 `#ffc53d`，带计数），点击走 `feed.toggleFavorite` 乐观更新；个人主页「收藏」tab 接通真实数据（PC + 移动端网格、分页、空状态、没有更多提示）
- 验证：接口冒烟 20 项（收藏/取消幂等、计数 ±1、详情与列表一致、私密作品拦截、未登录拦截、他人可看列表）+ 无头 Edge 13 项（单作品页点星标点亮与计数 0→1、刷新后保持、主页收藏 tab 出现该作品、PC 星标同一条作品同步、PC 取消收藏并刷新仍为未收藏、无控制台错误）

### 步 2：富媒体输入（评论图片 / 私信图片与表情 / 作品卡片消息）
- 数据（`db/init/010_m10_message_media.sql`）：`comment.image_urls`（VARCHAR 1200，最多 3 张）；`private_message.type`（TEXT / IMAGE / POST_CARD）、`image_urls`（VARCHAR 2000，最多 9 张）、`post_id`（卡片指向的作品）。实体用 `JacksonTypeHandler` 存 JSON 数组，`autoResultMap = true`
- 评论：`CreateCommentRequest` 的 content 改为可空 + `images` 最多 3 张，文字与图片至少一个非空（纯图片评论正文存空串）；`CommentVO.images` 返回续期后的访问地址；通知摘要对纯图片评论显示「[图片]」
- 私信：`POST /api/messages` 支持 `type` + `imageUrls` + `postId`，三类校验（TEXT 需文字、IMAGE 需 1~9 张、POST_CARD 需作品存在且对发送者可见），三类消息同样走「非互关先发方限 1 条」的 `sendState()`；`PrivateMessageVO` 返回 `type` / `images` / `post` 卡片（标题、封面、作者、`available` 表示作品是否还在）；会话列表摘要按类型显示 文本 / `[图片]` / `[作品] 标题`
- 前端新增公共组件：`RichInput.vue`（表情按钮 + 输入框 + 图片缩略图条 + 无内容时「+」/ 有内容时「发送」的按钮切换，评论最多 3 张、私信最多 9 张，上传进度覆盖层）、`EmojiPanel.vue`（5 组共 80 个 emoji，点选插入光标处）、`ImageViewer.vue`（全屏大图，左右切换 + 键盘 Esc/←/→）、`utils/upload.js`（预签名直传 MinIO，原生 XHR 不带 Authorization 头）
- 评论面板：底部换成 `RichInput`，评论与回复的图片渲染成缩略图宫格，点开大图；聊天页：图片消息宫格、作品卡片（封面 + 标题 + 作者，点击进单作品页 `/post/{id}`，作品已删除时提示），输入框换成 `RichInput`
- 踩坑：子组件里改「父组件数组中的原始对象」不会触发响应式（父组件 ref 里存的是代理，子组件拿到的是原始对象），图片列表改为在子组件内维护响应式副本再同步出去，上传进度与「上传完成」状态才会刷新
- 验证：接口冒烟 23 项（带图/纯图评论、4 张被拒、空内容被拒、图片地址续期、三类私信、两类参数校验失败、卡片 available、会话摘要、非互关限制）+ 无头 Edge 20 项（表情面板 80 个可插入、`+`/发送切换、CDP 注入文件上传成功、评论图片点击看大图并可关闭、聊天图片消息与卡片渲染、会话列表摘要含 `[作品]`、无控制台错误）

### 步 3：可见性设置（点赞 / 收藏 / 粉丝 / 关注）
- 数据：`db/init/009_m9_privacy.sql` 新建 `user_privacy`（四列各自 PUBLIC / FRIENDS / PRIVATE，**没有记录 = 四项全公开**，正好对应「全部默认公开，包括收藏和喜欢」）；好友沿用「互相关注」判定，不新增好友关系表
- 接口：`GET /api/user/privacy`（读自己的设置）、`PUT /api/user/privacy`（整份覆盖保存，取值不合法直接拒绝）；`/api/user/{id}/likes`、`/favorites`、`/followers`、`/following` 四个列表接口统一接入 `assertCanView`，无权时返回业务码 403 + 「TA 的点赞仅自己可见」/「TA 的粉丝仅好友可见」；`UserProfileVO.viewerCanSee` 一次带回四项可否查看，前端不用逐个试探
- 前端新增 `views/SettingsView.vue`（路由 `/settings`，需要登录）：资料卡 + 四项分段控件「公开 / 好友 / 私密」，点选即保存（失败自动回滚并提示），另有退出登录与关于信息；个人主页 PC 按钮区的「账号设置」与移动端右上角抽屉里的「账号设置」都指向这一页
- 他人主页按可见性裁剪：不可见的分区**直接不渲染 tab**（点赞 / 收藏），关注·粉丝数不可点时不高亮、点了也不跳转（函数内双保险）；被隐藏的 tab 若正好是当前 tab 会自动回落到「作品」
- 受限列表页（直接输 URL 或从别处跳进来）：显示居中的锁形空状态与接口原文案（「TA 的关注仅好友可见」），不渲染任何用户条目，也不显示「没有更多了」
- 验证：接口冒烟 19 项（默认全公开 → 私密拦截含未登录/文案校验 → 好友互关可见 → 非好友拒绝 FRIENDS → 非法值拒绝 → 恢复全公开）+ 无头 Edge 17 项（设置页四项渲染与默认公开、点「私密」落库并刷新回显、他人主页隐藏点赞 tab 但保留公开的收藏 tab、粉丝数不可点、受限页锁定提示且 0 条目、PC 端同样裁剪、恢复公开后即时回归、互关文案 3 项回归）
### 步 4：分享面板 + 作品卡片
- 新增 `components/SharePanel.vue`：顶部是作品缩略卡（封面 + 标题 + 类型与作者），中间「最近聊过」好友头像横滑列表（取 `GET /api/conversations` 前 10），底部「复制链接 / 系统分享（手机支持时）」；移动端底部升起、PC 端 380px 居中弹层
- 分享给好友 = 直接发一条 `POST_CARD` 私信（复用现有的私信接口与「非互关先发方限 1 条」限制），发送中头像变暗、成功后提示「已发送给 XX」并自动关面板；没有会话时提示「还没有聊过的好友，去主页点私信」，未登录时提示登录后可用
- 复制链接 = `location.origin + '/post/{id}'`（**原来的 bug 修掉了**：之前复制的是首页 `/feed` 地址），剪贴板不可用时降级成面板内只读输入框，自动全选方便长按复制
- 验证：无头 Edge 17 项（移动端面板升起、缩略卡与好友列表、点好友后会话里真的出现 POST_CARD 且作品 id 正确、剪贴板内容为单作品地址且不含 /feed、点卡片进单作品页、PC 居中弹层几何校验、取消关闭）；测试消息跑完即从数据库清除，会话最后一条消息指针同步回滚
### 步 5：整体回归与收尾
- 接口回归：收藏 20 项、可见性 19 项、富媒体（评论图片 / 私信图片与作品卡片）23 项，全部通过；收藏与隐私的取消/恢复流程跑完不留残余
- 浏览器回归：互关文案 3 项、可见性 17 项、分享 17 项全部通过；另加一轮全站控制台体检（移动端 430×900 与 PC 1280×900 分别访问 /feed、/me、/settings、/user/351、/messages、/notifications、/publish、/post/307），24 项全部「无控制台报错、无失败请求」
- 测试残留清理：删除验证期间产生的私信并回滚会话的最后一条消息指针（会话 3 恢复成 9 条）、清掉指向已删评论的悬空通知；Redis 里只有正常业务键（点赞计数、已看列表、短信限流），无需清理
- 已发现的遗留项（未处理，留待上线前）：347 与测试号 13（13800138000）之间还留着一条历史测试会话「第一条」，可在上线前连同测试账号一起清掉；主包体积 1.1MB 的拆分优化仍未做
## 2026-09-18 表情面板交互修复（评论 + 私信）

- 发送成功后自动收起：评论（`CommentPanel.submit`）与私信（`ChatView.send`）在发送成功后调用 `RichInput` 暴露的 `closeEmoji()`，面板不再留在原位挡住输入
- 点空白自动收起：`RichInput` 在面板展开期间挂一个 `pointerdown`（捕获阶段）监听，命中组件之外（聊天消息区、评论列表、页面任意空白）立即收起；组件内部点击（表情按钮、表情格子、输入框）不受影响，卸载时移除监听
- 选表情的行为保持「插入光标处 + 面板不关」，方便连续挑几个表情
- 顺手给移动端与 PC 的互动栏按钮补上 `data-act="like" / "comment"`（原来只有 favorite / share 有），便于测试和调试定位
- 验证：无头 Edge 17 项全过——移动端聊天页展开/点空白收起/连选表情/发送后收起且消息落库、评论区展开/点评论列表空白收起/发送后收起且评论落库、PC 端聊天页同样三连；测试产生的消息与评论跑完即清（含通知与悬空引用）
### 表情与键盘互斥 + 私信头像（同日跟进）
- 插入表情不再主动 `focus()` 输入框：移动端点表情不会再弹键盘，只把表情插到光标处（输入框本来聚焦时才回写光标位置，未聚焦时按末尾追加）
- 打开表情面板时主动 `blur()` 输入框，键盘与表情面板只留一个；点输入框（`@focus`）立即收起表情面板，也就是「点文本框才弹键盘」
- 私信页自己发的那一侧补上头像：用登录用户资料（`fetchMe`，取不到就退回本地缓存）渲染，头像排在气泡右侧，和对方那侧对称；顺手把「是不是我发的」判定改成按对方 id 反推，避免本地缓存缺失时把两边头像搞反
- 验证：无头 Edge 17 项全过（自己那侧头像存在且与对方不同源、头像在气泡右侧、打开面板时输入框失焦、选完表情仍不聚焦且面板不关、真实点击输入框后面板收起且输入框获得焦点；评论区同样四项）；表情面板原有 17 项回归全过
## 2026-09-18 新私信提示（顶部浮层）

- 后端推送带上发送者资料：`MessagePushListener` 给接收方的那条 payload 多带一个 `peer`（昵称 + 续期后的头像），由 `MessageService.peerOf(userId)` 组装；给发送方自己其他端的推送保持原样，少一次查询
- 前端新增 `components/MessageToast.vue`（挂在 `App.vue` 全局）：收到私信时从顶部滑下一条提示，头像 + 昵称 + 消息摘要（图片显示「[图片]」、卡片显示「[作品] 标题」），4 秒自动收起，点整条直接进该会话，右侧 × 可手动关
- 不打扰规则：自己发的（多端同步）不弹；正停在 `/chat/{对方}` 里不弹；页面在后台（切走标签页）不弹，交给红点；同一个人连续发时合并成一条并递增「N 条新消息」，摘要始终是最新一条
- 位置：移动端顶部通栏（尊重安全区），PC 端右上角 320px 卡片；z-index 2800，评论区打开时也压在最上层
- 验证：无头 Edge 14 项全过（弹出与昵称/摘要/头像、动画结束后停在 top 12 且左右等距、连发合并计数、点击进会话并消失、会话内不打扰、自己发的不弹、4 秒自动收起、PC 右上角 320 宽卡片）；另跑全站控制台体检 24 项无报错、无失败请求；测试消息跑完即清
## 2026-09-18 视频加载卡顿排查与修复（转码瘦身 + 媒体缓存）

### 诊断（先量数据再动手）
- 媒体接口本身不慢：直连 8080 或经 Vite 5173 代理拉 100MB 文件约 0.6s（~170MB/s），Range 请求正常返回 206
- 真正的根因是**视频文件太重**：转码流水线只换编码不缩分辨率，`ffprobe` 实测已发布作品是 3456×2160 / 2560×1600、60fps、6~20 Mbps（293 号 40.6s 就有 100MB），手机上（尤其经 USB 共享/热点）几乎不可能流畅拉流
- 次要原因一：`/api/media/**` 响应带 Spring Security 默认的 `no-cache, no-store`，浏览器完全不缓存，每次回看都要重新下载
- 次要原因二：本机内存吃紧（15.2GB 总量只剩 ~1GB 可用，IDEA 占 2.4GB），加上之前测试脚本残留的 2 个无头 Edge 进程（已清理）

### 改动
- `ProcessFfmpegRunner.transcodeToMp4`：新增 `scale` 长边 ≤1920（横屏限宽、竖屏限高、小视频不放大）、`-r 30`、`-maxrate 4M -bufsize 8M`、`-g 60`（关键帧 2 秒，方便拖动进度条），仍保留 CRF23 + veryfast + `+faststart`
- `ProcessFfmpegRunner.extractCover`：封面从原图（4K、0.5MB）压到宽 ≤1280、`-q:v 3`（~100KB），信息流首屏封面加载明显变轻
- `MediaController.serve`：给媒体响应加 `Cache-Control: public, max-age=31536000, immutable`（对象名带随机 UUID，内容不变，可长缓存）；实测该响应头不会被 Spring Security 的默认缓存头覆盖，Range 请求同时带缓存头

### 存量数据
- 新增一次性维护脚本 `.devtools/migrate-legacy-videos.ps1`：把已发布的重码率视频/大封面按新参数重压后走预签名直传替换（上传后先 HEAD 校验媒体可读，再改数据库，避免悬空引用）
- 293：96.2MB / 19.9Mbps / 4K60 → **20.3MB / 4.0Mbps / 1920×1200@30**，封面 520KB → 96KB
- 296：67.4MB / 12.4Mbps → **21.9MB / 3.8Mbps / 1920×1200@30**，封面 424KB → 107KB
- 305：32.9MB / 6.1Mbps → **19.0MB / 3.4Mbps / 1920×1200@30**，封面 594KB → 143KB
- 顺带清掉一个 258MB 的废弃源文件：把它当作临时作品的 source 跑了一遍真实转码流水线（顺带验证新参数：258MB / 48Mbps → 21.2MB / 3.75Mbps / 1920×1200@30，转码完成后 worker 自动删除源文件），验证完的临时作品已删除（数据库行 + 视频 + 封面均已清理）

### 孤儿对象清理工具
- `.devtools/minio-delete.js`：SigV4 直连 MinIO 删除指定对象（本地 Dev 用，无需 mc）
- `.devtools/cleanup-orphan-objects.ps1`：比对数据库引用（作品源文件/视频/封面/图片、用户头像）与 MinIO 实际对象，列出孤儿；默认只列不删，加 `-Delete` 才删，且**跳过 24 小时内写入的对象**，避免误删「刚上传还没发布」的文件
- 首次执行清理 18 个孤儿（合计 204MB，含被替换的旧视频与大封面），清理后复查为 0

### 备注
- 本机开发环境内存紧张是「本地看着也卡」的一部分原因，建议关掉不用的 IDEA/浏览器标签；服务端部署时媒体建议由 Nginx 直接出（或对象存储直链），不要再经过 Spring 中转

## 2026-10-08 对标抖音的五项优化（404 / 双击点赞 / 预加载 / 搜索 / 拆包）

### 步 1：404 未知路由兜底页
- 原来乱输地址会渲染出一片空白（路由未匹配 → 空 router-view）；现在新增 `frontend/src/views/NotFoundView.vue`，并在 `router/index.js` 末尾加 `/:pathMatch(.*)*` 兜底路由
- 页面沿用米白 + 珊瑚红令牌：大号「404」+ 一句「这个页面走丢了」+「回首页 / 去搜索」两个按钮，手机与 PC 同一套自适应布局
- 验证：无头 Edge 访问乱路径 `/no/such/page`，404 页正常渲染、按钮可跳转、控制台无报错

### 步 2：双击点赞 + 爱心动效
- 移动端 `FeedItem.vue`：单击不再立刻播放/暂停，而是延迟 260ms 判定——期间出现第二击（双击）就取消单击动作、改为点赞并冒爱心；超时无第二击才执行播放/暂停
- 动效：以点击坐标为中心生成一颗粉色心（#ff4d6d），上浮 + 放大 + 淡出约 760ms，连点可连冒（图层 pointer-events:none 不挡交互）
- 语义与抖音一致：未赞 → 点赞 + 冒心；已赞 → 只冒心不取消
- PC 端 `PcFeedCard.vue` 同套逻辑（滚轮/拖拽翻页不受影响）；图文卡单击无操作、双击点赞
- 验证：无头 Edge 专项——移动端双击冒心且点赞态 liked、图文卡双击冒心；PC 图文卡爱心出现且 not-liked→liked、二次双击保持 liked

### 步 3：视频预加载（预热下两张）
- `FeedView.vue` 新增 `warmIndexes` computed：定位当前位置之后**最近的两张视频卡**（自动跳过图文），下标经 `:warm` prop 传给卡片；卡片内 `preload` 由 metadata 提升为 auto
- 只预热两张，兼顾切换流畅与带宽；与既有「当前卡 loadeddata 自动播」叠加，滑动/滚轮切换的等待感明显减少
- 验证：无头 Edge 断言预热集合与 `preload="auto"` 恰好落在正确的两个下标上；连续切换多条无停顿

### 步 4：搜索（用户 + 作品）
- 后端：
  - 新增 `com.shiguang.common.SearchText`：关键词归一化（trim、限长 50）+ LIKE 通配符转义（% _ \），空串即无有效关键词
  - 新增 `com.shiguang.search.SearchController`：`GET /api/search/users`（昵称匹配，返回 UserPublicVO + followedByMe）与 `GET /api/search/posts`（标题/简介匹配，返回 PostVO），均复用现有游标分页与 R / PageVO 包装
  - `FollowService.searchUsers` / `FeedService.searchPosts` 落在各自域内，复用既有 buildPage 与可见性规则
- 前端：
  - 新增 `api/search.js`；新增 `views/SearchView.vue`：输入框 350ms 防抖、「用户 / 作品」双 Tab、结果存 sessionStorage（返回不丢）、滚动到底自动加载下一页
  - 用户结果行（头像 + 昵称 + 签名 + 关注按钮）；作品结果为三列宫格（视频角标），点条目进单作品页；PC 限宽 640px，移动端全屏
- 入口：移动端首页左上角新增浮标 `.m-search-fab`；PC 顶栏加「搜索」按钮
- 验证：接口 curl 实测 `keyword=彭` → 彭于烨(347)、`keyword=光` → 4 条作品；无头 Edge 15 项全过（Tab 切换、防抖只发一次请求、滚动加载、会话恢复、两个入口）

### 步 5：前端拆包（Element Plus 按需引入）
- 原状：`main.js` 全量 `app.use(ElementPlus)` + 整包 CSS → 主包 1116KB（gzip 369KB）、CSS 365KB，是首屏白屏偏长的主因
- 现在：`main.js` 移除全量注册，组件内继续直接 `import { ElMessage, ElMessageBox }`（Vite 摇树只打用到的 JS）；CSS 改为按需四个：element-plus 的 base / el-message / el-message-box / el-overlay
- 效果：**主包 1116KB → 219KB（gzip 369KB → 84KB，约 -77%）；CSS 365KB → 20KB**
- 验证：`npm run build` 通过；E2E 全量回归确认消息提示、确认框与各页面渲染无回归

### 本批验证汇总
- 接口：两个搜索接口 curl 实测（用户/作品命中正确、字段与游标正常）
- 浏览器：E2E 15/15 PASS（搜索页、首页浮标、预热属性、双击爱心、404、PC 搜索入口），0 控制台错误
- 构建：前端 build 成功，产物体积如上
- 遗留：README 截图占位待补；移动端局域网发布直传仍指向 127.0.0.1（部署前统一处理）
## 2026-10-08 移动端滚动吸附兜底（部分手机浏览器一滑划过好几条）

### 问题
- 部分手机浏览器（尤其内置浏览器）对 CSS `scroll-snap-type` 支持不佳或完全不生效，首页信息流一滑就连续滑过好几条视频，停不在某一条上
- 同一页面在支持 `scroll-snap` 的浏览器（微信 X5、Chrome 等）里表现正常

### 改动（frontend/src/views/FeedView.vue）
- 新增 JS 触摸吸附兜底：滚动停止后 180ms 检查位置，未对齐时补一次平滑吸附
  - 位移 < 15% 屏：回弹到原卡片
  - 位移 15%~160% 屏：正好前进/后退一条（还原"一条一停"）
  - 位移 > 160% 屏：落到最近的整数条（大力甩动不做拉回，避免回弹突兀）
  - 连续快滑：吸附动画中再次触摸时以目标位置为基准，两次快滑 = 前进两条，不会被吞
- 只在"用户触摸过"的滚动上生效：程序化滚动（恢复观看位置、回第一条等）不受干预；评论打开、开机定位阶段自动跳过
- 对支持原生吸附的浏览器无副作用：松手时位置已对齐，兜底逻辑直接跳过
- CSS 增强：`.m-scroll > .feed-item` 加 `scroll-snap-stop: always`（支持该属性的浏览器一次甩动最多吸附一条）；`.m-scroll` 加 `overscroll-behavior-y: contain`（避免下拉误触浏览器刷新）

### 验证
- 新脚本 `.devtools/test-touch-snap.js`：关闭原生吸附模拟手机浏览器，10/10 通过（0.6 屏吸附、轻微位移回弹、1.2 屏前进一条、2.4 屏大甩落整数条、后退 0.7 屏退一条、无位移触摸不动、连续两次快滑前进两条、程序化滚动不被干预、无报错）
- 回归：`.devtools/test-v2-optimizations.js` 15/15 通过，0 控制台报错## 2026-10-08 移动端搜索挪右上角 / PC"索"字形补偿 / 朋友页上线

### 步 1：移动端首页搜索浮标移到右上角
- `.m-search-fab` 由 `left: 14px` 改为 `right: 14px`，仍与安全区（`env(safe-area-inset-top)`）同一高度；左上角留给"返回条"（用户作品流/点赞流），两者不再打架
- 验证：430×900 移动端实测浮标 `right=14px / left=378px`，仍在 `feed.mode === 'home' && !isSingle` 时显示

### 步 2：PC 顶栏"搜索"二字视觉高度不齐（"索"偏矮）
- 排查：canvas 度量显示两个字的 ascent/descent 完全一致（不是字体回退问题）；转到实际渲染像素测量（8 倍截图 + 逐列扫描墨迹）后确认——同字号下"搜"墨迹高 25.25px，"索"只有 24.875px（约矮 1.5%），属字形本身观感差异
- 修复：`FeedView.vue` 顶栏按钮里把"索"包一层 `.p-glyph-tall`，用 `transform: scaleY(1.015)` 做光学补偿（`transform-origin: center 62%` 保证基线不飘），stroke 宽度不受影响
- 验证：无头 Edge 读回 `transform: matrix(1, 0, 0, 1.015, 0, 0)`，按钮尺寸与其他导航项一致

### 步 3：朋友页（互关好友 + 朋友动态）
- 后端：
  - `FollowService.friendIds(userId)`：取"我关注的人"与"我的粉丝"的交集（互关），单次 `LIMIT 500` 兜底避免超长 IN
  - `FollowService.friends(...)`：互关列表，按关注时间倒序游标分页，复用 `toUserPage`（含 `followedByMe` / `matched`）
  - `FeedService.friendsFeed(...)`：朋友动态流——互关好友 + 自己 的 `PUBLISHED/PUBLIC` 作品，创建时间倒序游标分页，复用 `buildPage`（点赞/收藏状态、作者关注态一并带上）；好友 id 上限 `FRIEND_IDS_LIMIT = 200`
  - 新增接口：`GET /api/follow/friends`、`GET /api/feed/friends`（均需登录）
- 前端：
  - 新增 `api/friends.js`、`api/posts.js` 内 `fetchFriendsFeed`；新增 `views/FriendsView.vue`
  - 页面结构：标题 + 互关人数 + 右上角搜索入口；「我的好友」「朋友动态」双 Tab
    - 我的好友：头像 / 昵称 + "互相关注"标签 / 签名，右侧「私信」（进 `/chat/:id`）与「取消关注」；取消关注后立即从列表与动态流移除
    - 朋友动态：作品宫格（移动端 3 列 / PC 5 列居中限宽 1180px），带视频角标、点赞数、作者名，点进 `/post/:id`
    - 两处空状态都做了引导（"去找朋友" → 搜索页）
  - 路由新增 `/friends`（`requiresAuth`）；`BottomNav.vue` 的"朋友"从"开发中"占位改为真实跳转并支持高亮，顺手移除了不再使用的 `ElMessage` 引用
- 验证：
  - 接口实测（彭于烨 347 账号）：`/api/follow/friends` 返回 4 位互关好友、`/api/feed/friends` 返回 9 条好友作品并带正确作者/类型
  - 浏览器 E2E `.devtools/test-friends-ui.js` 13/13 PASS：移动端标题/好友条目/底部导航高亮/私信与取消关注按钮、动态 Tab 渲染 8 条（作者=彭于烨）、点作品进 `/post/316`、搜索浮标右上角；PC 端有顶栏无底栏、好友行居中限宽 620px、动态 5 列、字形补偿生效；0 控制台错误
  - `npm run build` 通过（`FriendsView` 独立分包 7.46KB / gzip 3.49KB）
  - 测试用的关注关系已全部回滚（350 与 347 恢复为互不关注，测试账号状态干净）

## 2026-10-08 播放量 / 观看历史 / PC 空格暂停

### 步 1：播放量（视频=播放、图文=浏览）
- 迁移：`db/init/011_m11_view_count.sql` → `ALTER TABLE post ADD COLUMN view_count BIGINT UNSIGNED NOT NULL DEFAULT 0 AFTER comment_count`；`Post` / `PostVO` 加 `viewCount`，`PostService.toVO()` 填充
- 计数不直接写库（与点赞同一套路，避免高频 UPDATE 打满数据库）：
  - 新增 `feed/ViewCountService.java`
    - `recordView(postId, userId)`：先 `SETNX view:dup:{postId}:{userId}`（TTL 24h）做"同一用户同一作品一天只算一次"的去重，是首次观看才 `HINCRBY view:pending p:{postId} 1`
    - `pendingDeltas(ids)`：一次 `HMGET` 批量取增量；Redis 异常时降级为只显示数据库值
    - `flushPendingCounts()`：Lua 脚本 `HGETALL + DEL` 原子弹出快照，再逐条 `UPDATE post SET view_count = view_count + ?`；落库抛异常时把增量加回 pending，不丢计数
  - 新增 `feed/ViewCountFlusher.java`：`@Scheduled(fixedDelayString = "${app.view.flush-interval-ms:30000}")`，默认 30 秒落库一次
- 埋点复用现有逻辑：`FeedSeenService.markSeen()`（首页每张卡片停留时触发）顺带调 `recordView()`，前端不需要多发一次请求
- 展示合并"数据库值 + 未落库增量"：`FeedService.buildPage()`（首页/主页/点赞/朋友动态列表全部复用）与 `PostService.getDetail()`（单作品页），所以刚看完立刻能看到 +1，不用等落库
- 前端展示：
  - 移动端 `FeedItem.vue`：左下文案区新增一行 `N 次播放 / 次浏览`（VIDEO=播放、IMAGE=浏览），拖动进度条时随文案一起隐藏
  - PC `PcFeedCard.vue`：信息栏在"短视频 · 日期"下方新增同样一行
  - 个人主页宫格：移动端底部改成 `.pf-cell-stats` 横排（视频显示 ▶ 播放量 + ♥ 点赞量），PC 悬浮层改成 `.pc-ov-stats` 横排；顺带把移动端"仅自己可见"小锁从与统计行重叠的左下角挪到右上角

### 步 2：观看历史
- 后端：`FeedSeenService` 新增 `pageSeenDesc(userId, offset, size)`（ZSET `reverseRange` 按最近观看倒序）与 `clear(userId)`
  - `FeedService.history()`：过滤已删除/非公开作品后分页，游标前缀 `hist_`（`parseHistoryOffset`）；`clearHistory()` 只清历史、不影响已累计播放量
  - 接口：`GET /api/feed/history`、`DELETE /api/feed/history`（需登录，仅自己可见）
- 前端：`api/posts.js` 加 `fetchHistory` / `clearHistory`；新增 `views/HistoryView.vue`
  - PC 顶栏 + 移动顶栏（返回 / 标题 / 清空），3 列宫格（PC 5 列限宽 1180px）、滚动分页、空状态引导"去逛逛"、清空带二次确认
- 入口：移动端个人主页右上角抽屉「观看历史」（在"编辑资料"下方）；PC 个人主页按钮组「观看历史」（在"账号设置"旁）
- 路由：`/history`（requiresAuth）

### 步 3：PC 空格键暂停/播放
- `PcFeedCard.vue` 的 `defineExpose` 补 `togglePlay` 与 `playing`
- `FeedView.vue` 的 `keydownHandler` 增加空格分支：输入框 / textarea / select / 可编辑区域聚焦时跳过（否则与输入冲突），`preventDefault()` 阻止按钮默认激活，作用于当前活动卡片
- 与原有 ↑/↓/PageUp/PageDown 翻页快捷键并存

### 验证
- 接口实测（350 账号）：`POST /api/feed/seen/315` → `view:pending p:315 = 1`、`view:dup:315:350` TTL 24h；重复 seen 不重复计数；30 秒后 `post.view_count` +1 且 pending 清空；`GET /api/posts/315` 与历史列表都读到 +1
- `DELETE /api/feed/history` → 列表清空；重新 seen 3 条 → 按最近观看倒序（309,315,316）正确
- 浏览器 E2E `.devtools/test-views-history.js` 6/6 PASS：移动端播放量行、历史页 18 条渲染、点历史进 `/post/:id`、PC 播放量行、CDP 真实按键空格暂停/恢复、0 控制台错误
- `.devtools/test-history-entries.js`：移动端抽屉「观看历史」入口与跳转、移动端/PC 宫格统计行与视频播放量均 PASS
- `.devtools/test-history-covers.js` PASS：18 张封面 18/18 加载成功
- 回归：`.devtools/test-friends-ui.js` 13/13、`.devtools/test-friends-dot.js` 4/4（先把 Redis seen 值改小构造出新动态，跑完自动写回）


## 2026-10-08 朋友动态改成沉浸式流（按好友发布时间倒序、不含自己）

### 后端
- `FeedService.friendsFeed()`：不再把"自己"塞进好友集合，只返回互关好友的 `PUBLISHED/PUBLIC` 作品；好友集合为空时直接返回空页（避免 `IN ()` 的非法 SQL）
- 排序沿用创建时间倒序 + id 倒序，游标分页，翻页不重复
- 接口校验：350 账号拿到 8 条（作者只有彭于烨）；347 账号拿到 1 条（作者 361），自己的作品 0 条；limit=3 翻两页共 6 条无重复、时间严格递减

### 前端
- `stores/feed.js`：新增 `mode = 'friends'` 与 `loadFriendsFirstPage()`；`loadMore()` 增加朋友动态分支（复用 `fetchFriendsFeed`）
- 新增路由 `/friends/feed`（复用 `FeedView.vue`：移动端竖屏吸附流，PC 渲染 PcFeedCard）
- `FeedView.vue`：
  - `loadFeedForQuery()` 识别 `route.name === 'friends-feed'`，进入/刷新时加载朋友动态
  - 顶部返回条支持朋友动态（标题「朋友动态」）；`onBack()` 回朋友页（有历史走 back，否则 replace('/friends')）
  - 空状态文案、`retryFeed()`、`goHome()` 的 scoped 判断都补上 friends
  - 底部导航在朋友动态里高亮「朋友」
  - 直接打开/刷新 `/friends/feed` 也会清朋友红点（先 `refreshFriendsUnread()` 再 `clearFriendsDot()`，避免与开机轮询抢时序）
- `stores/notification.js` 新增 `clearFriendsDot()`；`FriendsView.vue` 改用 store action（删掉本地重复实现和不再使用的 `markFriendsSeen` 导入）
- `FriendsView.vue`：移动端点「朋友动态」直接进 `/friends/feed`（不再用宫格）；PC 保留宫格不变；空状态文案更新

### 验证
- 新增 `.devtools/test-friends-immersive.js` 10/10 PASS：Tab 正常、移动端跳 `/friends/feed`、卡片 8 张且非宫格、返回条标题「朋友动态」、作者只有好友、底部导航高亮、`scroll-snap-type: y mandatory` 且卡高=视口高、可滑到第二张、返回回朋友页、PC 仍宫格
- 新增 `.devtools/test-friends-feed-play.js` 5/5 PASS：滑到视频卡自动播放（t=4.5s）、显示播放量、作者=好友、观看记录写入 Redis、直接打开朋友动态即清红点（seen 300→316）
- 更新 `.devtools/test-friends-ui.js` 移动端断言（动态 Tab 现在进沉浸流）→ 13/13 PASS
- 回归：`.devtools/test-friends-dot.js` 4/4、`.devtools/test-views-history.js` 7/7、`npm run build` 通过

### 补充：朋友动态返回位置记忆
- `saveHomeResume()` / `takeHomeResume()` 增加可选 key 参数，新增 `sg_friends_resume`（sessionStorage），与首页的位置记忆互不干扰
- `onBeforeRouteLeave`：离开 `/friends/feed` 时按首页同样方式记住当前作品 / 播放进度 / 封面帧
- 朋友动态分支消费该记录：`locatePost(postId, index)` + `resumeSeek`，从个人主页返回直接回到刚才那条并续播
- 验证：`.devtools/test-friends-feed-resume.js` 4/4 PASS（滑到第 4 张视频 → 点头像进 `/user/347` → 返回仍是第 4 张、视频从 4s 续到 8.7s、resume key 已消费）；`test-friends-immersive.js` 复跑 10/10；`npm run build` 通过


## 2026-10-08 PC 端补上「朋友」入口（顶栏导航）

### 改动
- `frontend/src/views/FeedView.vue` PC 迷你顶栏 `.p-topbar` 新增「朋友」按钮，位置在「首页」之后，点击进入 `/friends`
  - 未登录时先跳 `/login`（和消息/我的同样的处理）
- 顶栏高亮改为动态：`pcActive` 计算属性 —— 朋友动态流（`route.name === 'friends-feed'`）高亮「朋友」，其余（首页流 / 他人主页流 / 点赞流 / 单作品页）高亮「首页」（原来是写死首页常亮）
- 「朋友」按钮带未读红点：复用 `notification.friendsUnread`，新增 `.p-dot`（深色胶囊顶栏版本，7px 圆点、无白边，靠 padding 区不压字）

### 验证
- 新增 `.devtools/test-pc-topbar-friends.js` 13/13 PASS（1400×900）：顶栏标签顺序 首页/朋友/搜索/发布/消息/我的、首页高亮、朋友未高亮、红点在朋友按钮内、顶栏宽度未变形、点击朋友跳 `/friends`、朋友页 PC 顶栏渲染、朋友页无底部导航、`/friends/feed` 下「朋友」高亮且「首页」不高亮、进入后红点清除、点「首页」回 `/feed` 且高亮
  - 红点用 Redis 造数据：把 `feed:friends:seen:350` 置 1 → 红点出现；进入朋友动态后 Redis 写回 316（最新好友作品 id），红点消失
- 回归：`.devtools/test-friends-ui.js` 13/13、`test-friends-immersive.js` 10/10、`test-friends-feed-play.js` 5/5、`test-friends-feed-resume.js` 4/4、`test-friends-dot.js` 4/4（顺手把该脚本改成自己准备 Redis 前置数据，不再依赖残留状态）、`npm run build` 通过
- 已知无关失败：`test-pc-backbar.js` 因手机号 `13800000003` 触发短信频控（"发送太频繁"）拿不到 token 而中断，与本次改动无关

## 2026-10-08 存储层可插拔化 + 阿里云短信接入（为 OSS 与真实短信做准备）

### 背景
部署前要把对象存储从本机 MinIO 换成阿里云 OSS、把 mock 短信换成真实短信通道。先把代码改成可切换，OSS/短信参数到位后只改配置即可。

### 存储层重构（MinIO / OSS 可切换）
- `StorageService` 接口去掉 MinIO 类型泄漏：`stat/open` 不再 `throws io.minio.errors.ErrorResponseException`，改为统一的 `StorageException`（带 `notFound` 标记）
- 新增 `StorageException`：`notFound()` / `failure()`，上层 `MediaController` 只按 `isNotFound()` 决定 404 还是 502
- `presignedGetUrl` 改名 `publicUrl`：它本来就是"对象名 -> 浏览器可访问地址"，换 OSS 后语义更准（6 个 Service、11 处调用点同步改名）
- `MinioStorageService` 加 `@ConditionalOnProperty(app.storage.type=minio, matchIfMissing=true)`，内部把 MinIO 错误翻译成 `StorageException`
- 新增 `OssStorageService`（`app.storage.type=oss`）：
  - 双客户端：`presignClient` 用公网 endpoint 生成浏览器直传预签名（内网地址浏览器访问不到）；`dataClient` 用内网 endpoint，转码读写走内网免流量费
  - `publicUrl()` 直接返回 OSS/CDN 公网地址 —— **媒体字节不再经过应用服务器**
  - `public-base-url` 可覆盖，以后接 CDN 只改这一项
- `StorageProperties` 扩展：`type` + `oss{endpoint, internal-endpoint, public-base-url, access-key-id, access-key-secret, bucket}`
- `application.yml`：新增 `STORAGE_TYPE` 与 `OSS_*` 环境变量
- 数据库里存的一直是裸 object key（`videos/315/xxx.mp4`），所以**老数据零迁移**：切 OSS 只需把对象同步过去，DB 不用动

### 短信改造
- `SmsProperties` 增加 `aliyun{access-key-id, access-key-secret, sign-name, template-code, endpoint}` 与 `mockProvider()`
- 新增 `AliyunSmsProvider`（`app.sms.provider=aliyun`）：Dysmsapi SDK，连接/读取超时各 3s；错误码翻译成人话（`isv.BUSINESS_LIMIT_CONTROL` -> "发送太频繁" 等）；手机号日志脱敏，验证码不落日志
- **安全收口**：固定验证码 `SMS_MOCK_CODE` 现在只在 `provider=mock` 时生效；真实通道下即使误配也会被忽略并打 WARN（原来只要配了就是 123456，生产会全站固定码）
- **失败回滚**：短信通道调用失败时，回滚本次写入的验证码 / 冷却 / 小时计数，用户可以立刻重试（原来会出现"验证码已存、短信没发、还被冷却 60 秒"）

### 验证
- `mvn compile` 通过；新增 `SmsSendFailureTest` 3/3（失败回滚、mock 码被忽略、小时计数回退），`SmsCodeServiceTest` 4/4、`SmsCodeHourlyLimitTest` 1/1
- 全量 `mvn test`：62 个用例，5 个失败全部**在 HEAD 上同样失败**（用 `git worktree` 拉 HEAD 单独复验确认），与本次改动无关
- 存储回归：`test-media-regression.js` 6/6（媒体地址仍是 `/api/media/`、首屏媒体加载、视频播放、无 4xx/5xx）；`test-friends-immersive.js` 10/10、`test-views-history.js` 7/7
- 手工接口验证：存在对象 200（2.9MB）、不存在对象 404（新异常映射生效）、Range 请求 206

### 工具
- `.devtools/start-all.ps1` 支持加载 `.devtools/oss.env`（每行 KEY=VALUE，已 gitignore），切 OSS / 真实短信只改这一个文件
- 新增 `.devtools/oss.env.example` 模板（含每一项的含义与取值位置）

### 遗留
- **Maven 测试跑在开发库上**：本次全量测试往 dev 库插了 2 条测试作品（318/319）和几十个测试用户，已删掉那 2 条作品；建议后续给测试单独建库（`application-test.yml`），否则测试会污染甚至删掉真实数据
- 未知路径现在仍返回 500（`NoResourceFoundException` 被全局兜底），待修
## 2026-10-08 OSS 迁移打通（媒体直连 + 浏览器直传）

### 桶配置（shiguang-bucket，华北2 北京）
- ACL 公共读：媒体地址可直接给浏览器，字节流不再过应用服务器
- CORS：来源 `localhost:5173` / `localhost:4173` / `123.57.252.14`，方法 PUT/POST/GET/HEAD，**AllowedHeaders=`*`**，ExposeHeaders ETag，maxAge 600
- 生命周期：`source/` 前缀 1 天自动清理（上传中断留下的源文件兜底）
- 数据迁移：MinIO 43 个对象 117.26 MB 原 key 全量同步，校验 `MISSING=0 SIZE_MISMATCH=0`

### 顺手修掉的 3 个真 bug（都被 MinIO 的宽容掩盖了）
- **前导斜杠**：`MediaController` 的 `{*objectName}` 会把路径前导斜杠一起捕获（`/videos/315/x.mp4`）。MinIO 会归一化 URL 里的 `//` 所以一直没暴露，OSS 严格按 key 匹配就 404。新增 `normalizeObjectName()`：剥掉前导斜杠 + 拒绝空段 / `.` / `..`（顺带堵住目录穿越）
- **V1 签名带 Content-Type**：OSS 的 V1 签名把 Content-Type 算进签名字符串，而 SDK 的 `generatePresignedUrl` 不签它，浏览器直传带 `Content-Type: video/mp4` 就 `SignatureDoesNotMatch`（curl 不带这个头反而 200，很迷惑）。改用 `GeneratePresignedUrlRequest.setContentType(...)`
- **预签名走 http**：endpoint 没写协议时 SDK 默认 http，生产前端是 https，直传会被浏览器按混合内容拦掉。`StorageProperties` 新增 `resolvedPresignEndpoint()/resolvedDataEndpoint()` 强制 https

### CORS 预检 403（部署前必须知道）
桶 CORS 规则漏了 AllowedHeaders，浏览器直传会在 OPTIONS 预检就被 OSS 拒掉（`CORSResponse`），**而 curl 能传成功** —— 只看命令行很容易误判成"直传没问题"。已在 `setupBucket` 里补 `setAllowedHeaders(List.of("*"))`；来源白名单可用 `-Doss.corsOrigins=https://域名` 覆盖（别写 `*`，预签名地址本身就是凭证）

### 验证
- 媒体代理：全量 200 / 2,904,418B、Range 206 / 1024B、不存在对象 404、编码斜杠 400
- **浏览器 E2E 发布**（新增 `.devtools/test-oss-publish.js`，6/6）：选文件 → 预签名 → OPTIONS 200 → PUT 200 直传 OSS → 填标题 → 发布 → 转码 → 落地页自动播放 OSS 视频
- 转码链路：测试作品转码产出 `videos/{id}/xxx.mp4` + `covers/{id}/xxx.jpg`，**源文件转码后自动清理**（回查 404）
- 回归：`test-media-regression.js` 6/6（断言从"必须走 /api/media/"改成"OSS 域名或 /api/media/"，并把"当前卡片在播放"改成滚动到视频卡片再断言，避免首页随机顺序导致误报）、`test-friends-immersive.js` 10/10、`test-views-history.js` 7/7
- 单测：`SmsSendFailureTest` 3/3、`SmsCodeServiceTest` 4/4、`SmsCodeHourlyLimitTest` 1/1、`ContentFlowTest` 6/6

### 工具
- `OssOpsTool` 新增 `listPrefix`（`-Doss.prefix=videos/2026-10-08/`）和 `deleteKeys`（`-Doss.keys=a,b`），用来查 / 清测试残留对象
- 本轮测试产生的临时对象与测试作品（364/365）已全部清理

### 遗留
- 部署到北京 ECS 时把 `OSS_INTERNAL_ENDPOINT` 填成 `oss-cn-beijing-internal.aliyuncs.com`（本机不通，留空走公网）
- 域名定了之后重跑 `setupBucket` 并带 `-Doss.corsOrigins=https://你的域名`

### 手机端视频全部加载不了（CORS 拆分修复）
- 现象：PC（localhost:5173）视频正常，手机用局域网地址打开首页，视频全黑/加载不出来，图片正常
- 根因：`FeedItem.vue` 的 `<video crossorigin="anonymous">`（抓帧做"返回续播快照"用）会把播放变成 **CORS 请求**，而 OSS CORS 规则只放行了 `localhost:5173`，手机的 Origin 是 `http://192.168.x.x:5173`，响应没有 `Access-Control-Allow-Origin` 就直接加载失败。`<img>` 没这个属性，所以图片照常
- 修复：CORS 拆成两条规则 —— **读取** GET/HEAD 允许 `*`（媒体本来就公共读，放开来源不泄露东西），**上传** PUT/POST 仍只放行白名单来源
- 验证：手机来源 GET 返回 `ACAO: *`；白名单来源 PUT 预检 200、陌生来源 PUT 预检仍 403（`*` 规则不会把上传限制吃掉）；`test-lan-mobile.js` 8/8（用局域网 Origin 跑完整首页播放）
- 教训：以后改 CORS 一定要用**真实来源**测，命令行 curl 不带 Origin 时一切正常，很容易误判

### 顺手清掉一个污染数据（Maven 测试写进了开发库）
- 首页刷到一条"海边日落"，两张图都是 404：`images/a.jpg` / `images/b.jpg`，是 `ContentFlowTest` 用假 key 建的真实作品（user 435），被 OSS 的 ORB 拦成 `net::ERR_BLOCKED_BY_ORB`
- 已按正常接口（mock 验证码登录 435）删掉该作品，并做了一次全量对账：OSS 45 个对象 vs DB 27 处引用，**缺失 0**
- **只要还跑 `mvn test` 就还会再发生**：测试库隔离（`shiguang_test`）还没做，见前面的遗留项

### 上传白名单补局域网段（手机端能看不能发布）
- 实测：手机端（Origin `http://192.168.0.100:5173`）发视频，PUT 预检被拒 403 —— 读取放开了，但上传白名单只有 localhost，所以手机上"能刷不能发"
- 修复：上传白名单补 `http://192.168.*:5173`（OSS 的 AllowedOrigin 只允许一个 `*`，写 `192.168.*` 可以跨到 192.168.x.y）
- 当前上传白名单：`localhost:5173` / `localhost:4173` / `123.57.252.14` / `192.168.*:5173`
- 验证：局域网来源 PUT 预检 200、陌生来源与未登记域名仍 403；`test-lan-publish.js`（局域网 Origin 走完整发布流程）6/6，上传直传 OSS 成功并正常播放
- 部署提醒：绑域名后要把域名加进白名单（`-Doss.corsOrigins=https://域名,...`），否则线上"能看不能发"

### 本地 MinIO 正式下线
- 运行时早已切到 OSS（`OSS 存储已启用: bucket=shiguang-bucket`），MinIO 只是"同步后没删"的本地副本：数据目录 117.3 MB + 程序 107.88 MB
- 已确认无运行时依赖：只有 `MinioStorageService`（`app.storage.type=minio` 时才装配）和运维工具引用它
- 动作：停掉 MinIO 进程 → 删除 `.devtools\minio-data`（117.3 MB）和 `.devtools\minio`（107.88 MB），9000/9001 已释放
- `start-dev.ps1` 里的 MinIO 启动行已注释（附恢复说明），一键启动不会再拉起它
- 回归：媒体 6/6、局域网手机 8/8、朋友页 10/10、观看历史 7/7
- 注意：`STORAGE_TYPE` 默认值仍是 `minio`，所以**必须加载 `.devtools\oss.env`**（start-all.ps1 会自动加载）；手动 `mvnw spring-boot:run` 忘加载的话会退回 MinIO，而本机 MinIO 已经没了，媒体会全挂

### 测试 token 过期（排障备忘）
- `.devtools/logs/t350.txt`、`t347.txt` 是 access token，**有效期 2 小时**，过期后浏览器类测试会停在 `/login`（`/api/posts/feed` 是公开接口，用 curl 测只会看到 200，容易误判成"token 没问题"）
- 刷新方式：`sms-code`（mock 码 123456）→ `login` → 取 `data.accessToken` 覆盖对应文件；注意短信有 60 秒冷却、验证码一次性
- 用"当前登录用户"判活要打 `/api/user/me`（单数），不是 `/api/users/me`

### 生产配置收口（prod profile 落地 + 一个藏得很深的 dev 坑）
- `application.yml` 的 `spring.profiles.active` 改成 `${SPRING_PROFILES_ACTIVE:dev}`；CORS 来源抽成 `app.cors.allowed-origins`（dev 默认 `*`，prod 收紧）
- 新增 `application-prod.yml`：密钥类变量【一律不给默认值】，缺了直接启动失败并报出变量名，而不是悄悄退回 dev 弱口令；`SMS_PROVIDER` 默认 `aliyun`、`STORAGE_TYPE` 默认 `oss`、关掉 springdoc、日志落文件 + 轮转
- `GlobalExceptionHandler` 补 `NoResourceFoundException`(404) 和 `HttpRequestMethodNotSupportedException`(405)：以前访问不存在的路径（包括被关掉的 swagger 地址）会被兜底成 500
- prod 实测：`/swagger-ui/index.html`、`/v3/api-docs`、未知路径都返回 404；`POST /api/posts/feed` 返回 405；feed 正常 200；CORS 只放行 `http://123.57.252.14`，陌生来源没有 ACAO 头
- 新增 `.devtools/oss.env.example`（纳入版本管理）：服务器上要填的全部变量清单，逐项写明不填的后果
- 顺手清掉 MinIO 时代的死代码：`http.js` 里的 `fixMediaHost`、`vite.config.js` 里的 `/shiguang-media` 代理

### 一键启动脚本静默丢变量（dev 其实一直跑在 MinIO 实现上）
- 现象：dev 启动后 feed 返回的媒体地址是相对的 `/api/media/...`（MinIO 实现的特征），老素材整片 502
- 根因：`oss.env` 是无 BOM 的 UTF-8 且含中文注释，`start-all.ps1` 用 `Get-Content` 读它。Windows PowerShell 5.1 默认按 ANSI(GBK) 解码，中文注释末尾的多字节序列会把**换行一起吃掉**，紧跟在注释后面的 `STORAGE_TYPE=oss` 被并进上一行注释，于是静默没被加载 → `app.storage.type` 退回默认 `minio`
- 修复：改成 `[System.IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)`（先按行切分再解码，任何解码异常都不会再吞换行），并在加载后回显 `存储=oss / 短信=`
- 顺带给 `start-dev.ps1` 补上 UTF-8 BOM（同类问题的预防）
- 复验：启动日志出现 `OSS 存储已启用`，feed 返回 `https://shiguang-bucket...` 绝对地址；媒体 6/6、朋友页 10/10、观看历史 7/7
- 教训：脚本读"带中文的配置文件"必须显式指定编码；有默认值的配置项（`type: ${STORAGE_TYPE:minio}`）出问题时不会报错，只在功能上表现得很怪

### 真实短信改用「号码认证服务·短信认证」（pnvs），绕开签名资质
- 背景：标准短信服务的签名要人工审核，个人账号没有备案域名 / App / 小程序时基本过不了审
- 核到的官方事实（来自阿里云 OpenAPI 元数据，不是猜的）：
  - `SendSmsVerifyCode` 的 `SignName` 描述原文：暂不支持使用自定义签名，请使用【系统赠送的签名】，可在“赠送签名配置”页面选择
  - `TemplateCode` 描述原文：参数 SignName 选择赠送签名时，必须搭配【赠送模板】下发短信，示例值形如 `100001`
  - 即：不申请签名、不申请模板、不走人工审核；顺带自带验证码长度 / 有效期 / 频控 / 失败时自动换签名重试
  - SDK：`com.aliyun:dypnsapi20170525:2.0.0`（与 dysmsapi 同属 tea-openapi 体系，Maven 解析无冲突）
- 代码改动（业务逻辑零改动，因为早就留了 `SmsProvider` 接口）：
  - 新增 `PnvsSmsProvider`（`app.sms.provider=pnvs` 时装配），验证码仍由我们自己生成、存 Redis、自己校验
  - `SmsProperties` 增加 `pnvs` 配置块；`application.yml` 增加对应项；`application-prod.yml` 默认 provider 由 `aliyun` 改为 `pnvs`
  - 赠送模板的变量个数不固定，所以 `template-param` 做成可配置（默认 `{"code":"{code}","min":"{min}"}`），变量对不上时按模板原文改这一项即可
- 实测：
  - 用假 AK 起实例 → 日志打出 `阿里云短信认证已启用: endpoint=dypnsapi.aliyuncs.com`；调接口后阿里云返回 `404 Specified access key is not found`，说明请求确实打到了阿里云、V3 签名链路走通，且异常被正确降级成用户友好提示
  - 故意不配 key 起实例 → 启动直接失败并报出：`app.sms.provider=pnvs 时必须配置 access-key-id / access-key-secret / sign-name / template-code`
  - dev 回归：mock 通道仍正常（验证码 123456 登录成功）
- 代价（明确记下来）：短信里显示的签名由阿里云指定，不是「拾光」；赠送模板文案不可改。等以后有备案域名或小程序，再申请自己的签名换回 `SMS_PROVIDER=aliyun`
- 待办：控制台里「赠送签名配置」「赠送模板配置」各选一个，把签名名称和模板 CODE 填进 `.devtools/oss.env`

### 真实短信链路跑通（真机验证）
- 控制台选定的赠送资源：签名「速通互联验证服务」、模板 `100001`
- 模板文案：`您的验证码为${code}。尊敬的客户，以上验证码${min}分钟内有效，请注意保密，切勿告知他人`
  → 变量正好是 `${code}` + `${min}`，与代码里的默认 template-param 一致，未做额外配置；`min` 取 `SMS_CODE_EXPIRE_MINUTES`（5），与 Redis 里验证码的 TTL 同源
- 真机结果：本地临时以 `SMS_PROVIDER=pnvs` 起实例，用户在自己手机上完成"发送验证码 → 收到 → 登录"全流程；后端日志 `短信认证已发送 phone=150****1015 bizId=538912991543054626^0`
- 验证完已把本地切回 mock（`SMS_PROVIDER` 留空），避免以后跑自动化测试时误发真实短信；日志确认回落为 `【拾光短信-Mock】`
- 服务器上无需额外设置：`application-prod.yml` 里 provider 默认就是 `pnvs`
- 遗留提醒：短信里显示的签名是阿里云赠送的「速通互联验证服务」，不是「拾光」；等以后有备案域名或小程序，再申请自己的签名并切 `SMS_PROVIDER=aliyun`

### M12 管理员作品管理（角色 + 全量作品后台）
- 需求：指定账号（管理员手机号，见本地 README「管理员账号」，不进仓库）要有最高权限，可以对**所有人**的作品做管理（本轮范围只做作品：查 / 下架 / 恢复 / 删除）
- 设计文档：`docs/superpowers/specs/2026-10-09-admin-console-design.md`
- 数据变更（`db/init/012_m12_admin.sql`）：
  - `user.role` VARCHAR(20) 默认 `USER`（枚举 `UserRole`：USER / ADMIN，MyBatis 按名字映射，和 `PostStatus` 同一套路）
  - `post.block_reason` VARCHAR(200)，`post.status` 注释补上 `BLOCKED`（列本身就是 VARCHAR(20)，不用改类型）
  - 新增索引 `idx_status_created(status, created_at)`：管理列表按状态过滤 + 时间倒序，顺带惠及首页流
- 关键决策：**用 `status=BLOCKED` 而不是新加一列**。所有"只该看到正常作品"的查询本来就带 `status=PUBLISHED`（首页流、朋友流、他人主页、点赞、收藏、搜索、历史、互动校验共 8 处），新增枚举值后这些地方**一行都不用改**，自动把下架作品排除干净；新加列则要逐个补条件，漏一处就是漏洞
- 权限：JWT 里只有 userId，所以管理接口**每次查一次库**确认角色（管理流量小，且改角色立即生效，不用等 token 过期）；非管理员一律 403；`GET /api/admin/me` 只回 `{admin:true/false}` 供前端入口显隐
- 新增接口（`com.shiguang.admin`）：
  - `GET /api/admin/me`
  - `GET /api/admin/posts`（`status` / `visibility` / `authorKeyword`（昵称或手机号）/ `postId` / `cursor` / `limit`，游标格式与 feed 一致）
  - `POST /api/admin/posts/{id}/block`（body `{"reason":"..."}` ≤200 字，重复下架幂等、原因保留首次）
  - `POST /api/admin/posts/{id}/unblock`（只对 BLOCKED 生效）
  - `DELETE /api/admin/posts/{id}`（硬删，复用原有 delete：OSS 对象 + 点赞 / 收藏 / 评论级联清理 + 缓存失效事件）
- 边界：下架只对 `PUBLISHED` 生效（PROCESSING / FAILED 报业务错误，没有意义）；删除对任何状态都允许；作者改可见性救不回被下架的作品
- 作者侧：`FeedService.userPosts` 在 `own=true` 时不过滤 status，所以作者自己主页照常看得见被下架的作品 —— 加「已下架」角标（与私密锁同位置，二者互斥显示），详情里显示 `该作品已被管理员下架：<原因>`
- 顺带补了一个泄漏口：`GET /api/posts/{id}` 是公开接口，原来只挡了 PRIVATE，现在被下架的详情对非作者也返回 404
- 前端：新增 `/admin`（`AdminView.vue`，移动端深色 / PC 浅色，原生控件不依赖 Element Plus 表单组件），入口在个人主页（移动端「⋯」抽屉 + PC 操作区按钮），仅 `admin=true` 时出现
- 验证：
  - 接口层：非管理员 403、`admin/me` 两种身份、下架后匿名详情 404 而作者 200（带 blockReason）、他人主页 / feed / 搜索 / 点赞列表都查不到、重复下架幂等、PROCESSING 不可下架、恢复后匿名详情恢复 200、删除后列表为空且再删 404
  - 端到端 `.devtools/test-admin-console.js`：移动端抽屉入口 → 管理页筛选 → 下架（原因回显）→ 自己主页角标 → 详情提示 → 恢复 → 非管理员无权限（且抽屉无入口）→ PC 端渲染与按钮，**16/16 通过**
  - 回归：媒体 6/6、朋友页 10/10、观看历史 7/7；测试数据已复原（无残留 BLOCKED 记录）
- 坑：同一文件的多个 `apply_patch` 并发提交会互相覆盖（工具仍报 Success）—— 之后改同一文件要串行 + 改完 `rg` 复核

### 部署前体检（2026-10-10）
- 目标：上线前把整仓过一遍，确认没有"只在开发环境成立"的东西被带上生产
- 数据库：`db/init/002_m2_content.sql` 原来只对老库可用（全新库执行会 `Can't DROP 'cover_url'` 直接中断部署）。
  改成 `information_schema` 判断 + 存储过程 `sg_m2_upgrade`，同时兼容"全新安装"与"老库升级"两条路径。
  验证：全新库跑完 12 个脚本，结构 90 列 / 33 索引与开发库逐列逐索引一致；老库升级路径单独建库验证通过
- 测试：修掉 4 个历史遗留失败（`PostServiceTest` 缺 `ViewCountService` / `FollowService` mock、断言停在
  `TranscodePublisher` 时代；`FollowServiceTest` 缺 `ApplicationEventPublisher`；`InteractionFlowTest`
  的删除权限断言没跟上"评论作者或作品作者可删"的新规则，改为覆盖第三方 403 / 作品作者可删 / 本人可删）。
  `mvn clean package` **69/69 通过**，产出可运行 jar
- 前端：登录页"开发环境验证码为 123456"改为构建期折叠（`import.meta.env.DEV ? ... : ''`），
  线上 chunk 里既无提示文案也无 123456 字样；`npm run build` 通过（dist 42 文件 / 574KB，路由级拆包已成）
- WebSocket 加固：`/ws` 原来的 `setAllowedOriginPatterns("*")` 收口到与 HTTP CORS 同一份白名单
  （`app.cors.allowed-origins`，prod 默认站点自身来源）。实测：白名单来源 + 合法 token → 101 升级成功，
  外来来源 → 403，无 Origin（非浏览器客户端）→ 101
- 死代码清理：删掉没有任何页面渲染的 `PcSideNav.vue` / `PcRightPanel.vue`；`PcRightPanel` 里那段
  "已被管理员下架"提示只存在于死代码里，已挪到真正在用的 `PcFeedCard.vue`（顺带补上 PC 端看不到下架原因的问题）
- `GlobalExceptionHandler` 补 3 类客户端错误：缺必填参数 / 参数类型不对 / 请求体不是合法 JSON。
  原来一律回 500，实测 `/api/notifications` 不带 `category` 会显示"服务器开小差了"；现在都回 400 并带上具体参数名
- jar 冒烟（打包产物真跑一遍）：匿名 feed、登录、`/api/user/me`、`/api/admin/me`（admin=true）、详情、
  观看历史、通知、会话、搜索用户/作品、预签名上传全部正常；媒体地址全部指向 OSS `shiguang-bucket`；
  短信 60 秒冷却、验证码单次有效、验证码不随接口返回，均符合预期
- 安全复查：仓库无硬编码密钥；prod 缺密钥直接启动失败；Swagger 线上关闭；CORS / JWT / OSS / 短信
  密钥类配置全部走环境变量；dev 弱口令只存在 `application-dev.yml`
- 待办（部署时做）：服务器装 JDK21 / MySQL / Redis / RabbitMQ / ffmpeg、导入 12 个 SQL、systemd 托管 jar、
  Nginx 托管 dist 并反代 `/api` 与 `/ws`（对 `/ws` 关掉 access_log 免得 token 进日志）、
  用 SQL 把管理员手机号置为 ADMIN；`/ws?token=` 走 query 的老问题改由 nginx 日志侧规避


### 部署上线（2026-10-10）
- 服务器：阿里云 ECS（Ubuntu 22.04，2C4G，50G 盘），公网 IP `123.57.252.14`，当前为 IP 直连、无 HTTPS
- 安装清单：OpenJDK 21.0.12 / MySQL 8.0.46 / Redis 6.0.16 / RabbitMQ 3.9.27 / ffmpeg 4.4.2 / nginx 1.18.0；加了 2G swap（4G 小内存机器保险）
- 数据库：建库 `shiguang` + 账号 `shiguang@127.0.0.1`，按顺序导入 `db/init` 下 12 个脚本（11 张表）；MySQL 小内存调优（innodb_buffer_pool 256M、关 performance_schema）
- 中间件：Redis 开 requirepass；RabbitMQ 建 `shiguang` 用户 + 全权限、内存水位 0.4、删除 guest
- 部署形态与仓库里准备的一致：systemd 托管 `/opt/shiguang/app.jar`（环境变量走 `/opt/shiguang/shiguang.env`，权限 600）+ nginx 托管 `/opt/shiguang/dist` 并反代 `/api`、`/ws`（`/ws` 关闭 access_log，token 不进日志）
- 产物：后端 `mvn -DskipTests package`（84.9 MB jar）；前端 `npm run build`
- 外网实测：`GET /` 200（SPA 首页）；`/assets/*`、favicon 200；`POST /api/auth/sms-code` 打到了阿里云（假号码返回"非法参数"，说明 AK / pnvs 通道正常）；构建产物里 grep 无 localhost / 8080 残留
- 管理员：预置 `15035271015`（彭于烨，id=1）为 ADMIN，首次登录即生效；生产库当前无内容，首页会是空状态
- SSH 备忘：Windows OpenSSH 9.5 的 hostbound 兼容问题用 `.devtools/ssh-run.py`（paramiko）绕过；原私钥被加密不可用，已重新生成免密密钥并装到服务器（旧文件备份为 `*.encrypted.bak`）
- 待办：
  - 真机登录（真实短信）+ 发布一条作品，验证 OSS 直传 / 转码 / 播放全链路
  - 域名备案通过后：nginx 换 server_name + 配 443 HTTPS（现在是 IP 直连）
  - MySQL 定期备份（尚未配置）
  - OSS 上 12 个孤儿对象（约 102MB，此前已列清单）可清理