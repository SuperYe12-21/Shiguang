# 私信 IM（消息系统里程碑 3）设计

日期：2026-09-18

## 目标与已确认的决策

- 一对一文本私信：会话列表 + 聊天页
- 可聊范围：**互相关注可自由聊；非互关时先发的一方最多发 1 条，等对方回复后解锁**（已确认的方案 B）
- 消息页做抖音式分区：赞与收藏 / 评论 / 新增关注 / 私信 四个入口，私信进会话列表
- 实时性：WebSocket 推送新消息与未读变化；30 秒轮询保留为断线兜底

## 数据模型（db/init/006_m6_message.sql）

### conversation（会话，一对用户一条）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| user_a_id / user_b_id | BIGINT | 两个用户，约定 id 小的在 user_a_id，保证 (a,b) 唯一 |
| last_message_id | BIGINT NULL | 最后一条消息 id |
| last_message_at | DATETIME NULL | 最后消息时间，会话列表排序键 |
| a_last_read_id / b_last_read_id | BIGINT | 双方已读到的最大 message id，默认 0 |
| created_at / updated_at | DATETIME | 时间戳 |

唯一键 `uk_pair(user_a_id, user_b_id)`；索引 `idx_a_active(user_a_id, last_message_at)`、`idx_b_active(user_b_id, last_message_at)`。

### private_message（私信消息）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键，也是分页游标 |
| conversation_id | BIGINT | 所属会话 |
| sender_id / receiver_id | BIGINT | 发送方 / 接收方 |
| content | VARCHAR(1000) | 文本内容 |
| created_at | DATETIME | 发送时间 |

索引：`idx_conv(conversation_id, id)`、`idx_receiver(receiver_id, id)`。

## 后端（com.shiguang.message）

### REST 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /api/conversations | 会话列表，游标分页（cursor=last_message_at 毫秒字符串），按最近消息倒序；含对方信息、最后一条消息摘要、未读数 |
| GET | /api/conversations/{peerId}/messages | 聊天页数据：首次带 peer 与「能否发送」状态；cursor 加载更早消息（倒序，每页 30） |
| POST | /api/messages | 发送消息 { toUserId, content }，返回新消息 |
| POST | /api/conversations/{peerId}/read | 把该会话中我收到的消息标记已读，返回最新未读总数 |
| GET | /api/messages/unread | 私信未读总数（红点用），返回 { messages: N } |

### 发送校验（方案 B）

1. content 去空白后非空、≤1000 字；不能发给自己；接收方必须存在
2. 双方互关 → 直接放行
3. 非互关：若对方在本会话中从未发过消息，且我已发过 ≥1 条 → 拒绝（BizException 1，「对方回复后才能继续聊天」）；否则放行
4. 会话按需创建（唯一键 + DuplicateKey 兜底）；更新 last_message_id / last_message_at
5. 事务提交后通过 WebSocket 推给双方（多端同步）

### WebSocket

- 端点 `/ws`；握手用 query 参数 `?token=JWT`，HandshakeInterceptor 复用 JwtService.parse 校验，失败拒绝握手
- SocketSessions 维护 userId → Set<WebSocketSession>（支持多标签页/多端）
- 协议（JSON）：
  - 服务端 → 客户端：`{ "type": "message", "data": PrivateMessageVO }`（双方都推）、`{ "type": "unread", "data": { "messages": N } }`（接收方未读变化）、`{ "type": "pong" }`
  - 客户端 → 服务端：`{ "type": "ping" }`
- 连接只影响实时性：REST 始终可用，未读以接口为准，轮询兜底

### 未读计算

- 单会话：`COUNT(private_message WHERE conversation_id=? AND receiver_id=me AND id > my_last_read_id)`
- 全局：JOIN conversation 按双方各自的 last_read_id 判断（Mapper @Select）
- 已读：把 my_last_read_id 推进到该会话最大 message id

## 前端

### 新增

- `api/messages.js`：五个接口封装
- `stores/message.js`：私信未读、WebSocket 单例（登录连、登出断、断线 3s 重连、30s 心跳）、消息分发
- `views/MessagesView.vue`：**消息页（私信是页面主体，默认展示）**——上方三个互动消息入口（赞与收藏 / 评论 / 新增关注，各带分类未读角标，点击跳互动列表），下方「私信」分区直接列出会话（头像/昵称/摘要/时间/未读数字），点击进聊天页，空状态引导
- `views/ChatView.vue`：聊天页——消息气泡（自己靠右）、上滑加载更早、底部输入发送、非互关限制提示条、进入与收到时自动已读

### 改造

- `NotificationView.vue`：标题改为「互动消息」，保留三个分类 Tab（从消息页的入口进来时默认选中对应分类），不做私信页签——私信不再是一个需要点击的入口，而是消息页的默认内容
- 消息红点：底部导航与 PC 侧栏 = 通知未读 + 私信未读（store 合并计算）
- 所有「消息」入口（底部导航、PC 顶栏、PC 个人主页侧栏、单作品页兜底）统一指向 `/messages`
- `App.vue`：登录启动 / 登出停止 message store 的 socket
- `vite.config.js`：`/ws` 代理到 8080（ws: true）
- `router/index.js`：新增 `/messages`、`/chat/:userId`
- `ProfileView.vue`：他人主页资料区加「私信」按钮（点击进聊天页）

## 不做（YAGNI）

图片/语音消息、撤回、删除会话、给对方展示已读回执、正在输入状态、群聊。

## 验收标准

- 接口冒烟：互关自由发、非互关第 2 条被拒、对方回复后解锁、已读推进、未读计数、会话列表排序、分页
- e2e（无头 Edge，移动端 + PC）：A 发消息 B 无刷新实时收到、未读红点变化、消息页四分区、聊天页气泡方向、返回路径正常
- 断线场景：WS 断开时轮询兜底仍能刷新未读
