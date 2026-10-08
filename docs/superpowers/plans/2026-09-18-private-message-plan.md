# 私信 IM 实施计划（消息系统里程碑 3）

> 状态：12 步全部完成（2026-09-18）。接口冒烟通过；无头 Edge 端到端移动端 430×900 + PC 1400×900 共 45 项断言通过（favicon 404 已顺带修掉）；额外验证了「WS 被阻断时 30 秒轮询兜底」28 秒内刷新红点。

## 步骤清单

1. 数据库：`db/init/006_m6_message.sql`（conversation + private_message）
2. 后端：pom.xml 加 spring-boot-starter-websocket；SecurityConfig 放行 `/ws/**`（握手层校验 JWT）
3. 后端：实体与 Mapper（Conversation / PrivateMessage / ConversationMapper / PrivateMessageMapper）
4. 后端：MessageService（发送校验、会话列表、聊天分页、已读、未读）
5. 后端：MessageController（5 个 REST 接口）
6. 后端：WebSocket（WebSocketConfig / MessageSocketHandler / SocketSessions / 握手拦截 / AFTER_COMMIT 推送）
7. 验证：接口冒烟（两账号互发 + 限一条 + 已读 + 未读）
8. 前端：api/messages.js + stores/message.js（socket 生命周期）
9. 前端：MessagesView（会话列表）+ ChatView（聊天页）
10. 前端：NotificationView 四分区改造 + 红点整合 + 路由 + App.vue + vite /ws 代理 + ProfileView 私信按钮
11. 验证：无头 Edge e2e（移动端 430x900 + PC 1400x900）
12. 文档：PROGRESS.md 追加本里程碑

## 验证方式

- 后端：`mvnw -DskipTests compile` + curl 冒烟（token 取自 .devtools/logs/t347.txt / t351.txt）
- 前端：`npm run build` + 无头 Edge CDP 脚本（复用 logs 下 e2e 脚本模式）
