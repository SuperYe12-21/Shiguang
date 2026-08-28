# 拾光（Shiguang）社交平台设计文档

- 日期：2026-08-28
- 状态：已与用户确认，等待审阅
- 项目根目录：`D:\Shiguang`
- 产品定位：抖音式短视频/图文分享社交平台（求职作品，可真实部署供他人访问）

## 1. 项目概述

「拾光」是一个记录生活高光时刻的社交平台：用户通过手机号注册登录，发布短视频或图文，在推荐流中浏览他人作品，通过点赞、评论、关注与他人互动。产品参考抖音的沉浸式信息流与互动方式，视觉采用「清新明亮」风格（米白底 + 暖珊瑚色强调）。

- 用户形态：手机 H5 + PC 网页（同一套 Vue 3 响应式代码）
- 规模预期：求职作品级，目标数百至千级日活，并发量级百级 QPS 内
- 质量要求：美观、流畅、可维护、可一键部署上线

## 2. 范围

### V1（本版必做）

- 注册登录：手机号 + 短信验证码（开发期 Mock Provider，上线可切阿里云短信）
- 首页推荐流：视频/图文混合，时间倒序 + 轻量热度排序
- 发布：上传视频/图片、标题/描述、上传进度、发布状态（转码中/已发布/失败）
- 互动：点赞（作品/评论）、评论（发表/删除/点赞）
- 用户：我的主页、他人主页、编辑资料、关注/取关

### V1.5 / V2（本版不做，预留）

- V1.5：朋友页（关注动态）、消息通知（点赞/评论/新粉丝）、轻量搜索
- V2：私信、楼中楼回复、话题标签、完整搜索、推荐算法升级、直播

## 3. 技术选型

| 层 | 选型 | 说明 |
| --- | --- | --- |
| 后端 | Java 21 + Spring Boot 3.5.16 + Maven | Boot 3.5 生态兼容性最稳（MyBatis-Plus / springdoc 等） |
| ORM | MyBatis-Plus | 国内企业主流，分页/条件查询开箱即用 |
| 数据库 | MySQL 8 | 业务数据 |
| 缓存 | Redis | 验证码、信息流缓存、点赞计数缓冲 |
| 消息队列 | RabbitMQ | 视频转码、计数落库等异步任务 |
| 认证 | Spring Security + JWT | 登录态 |
| 对象存储 | MinIO（本地/开发）→ OSS（上线可切换） | 视频、图片、封面 |
| 转码 | FFmpeg | 统一转 H.264 + 抽帧封面 |
| 前端 | Vue 3 + Vite + Pinia + Vue Router | 响应式，PC 用 Element Plus，移动端自研组件 |
| 接口文档 | springdoc-openapi | OpenAPI 3 自动生成 |
| 部署 | Docker + docker-compose + Nginx + HTTPS | 一键部署 |

## 4. 视觉与交互设计（已确认）

- 风格：清新明亮——米白底 `#f7f7f5`、暖珊瑚强调 `#ff5c5c`、圆角卡片、柔和渐变（参考小红书视觉 + 抖音交互）
- 移动端布局：全屏竖屏视频流；右侧竖排互动栏（头像+关注、点赞、评论、收藏、分享）；底部导航（首页/朋友/＋发布/消息/我，V1 中「朋友」「消息」置灰）
- PC 布局（已选方案 A）：暖色渐变背景 + 窄图标导航栏 + 居中视频流 + 右侧毛玻璃评论/推荐面板
- 页面清单：登录注册页、首页推荐流、发布页、评论面板、个人主页/他人主页、编辑资料页
- 交互要点：图片懒加载、视频预加载、骨架屏、上传进度条、发布状态反馈

## 5. 系统架构

模块化单体 + 异步增强：

```
用户（手机 H5 / PC）
   ↓ HTTPS
Nginx（静态资源 / 反向代理 / 证书）
   ↓ /api
Spring Boot 单体（Controller → Service → Mapper）
  ├── auth：登录注册、验证码、JWT
  ├── user：用户信息、关注
  ├── content：发布、视频转码状态机
  ├── feed：推荐流
  └── interaction：点赞、评论
   ↓ 读写            ↓ 缓存/计数       ↓ 异步
MySQL 8            Redis            RabbitMQ
                                      ├── 转码 Worker（FFmpeg）
                                      └── 计数落库 / 通知（预留）
   ↓ 写入
OSS 对象存储（视频/图片/封面）
```

- 模块边界按「可拆分」标准设计：未来如需微服务化，可按 auth/user/content/feed/interaction 拆分，无需重写业务逻辑
- 统一响应体、统一异常处理、OpenAPI 文档随代码生成

## 6. 数据模型（7 张业务表 + Redis）

| 表 | 关键字段 | 约束/说明 |
| --- | --- | --- |
| user | id, phone, nickname, avatar_url, bio, created_at | phone 唯一 |
| follow | id, follower_id, followee_id, created_at | 唯一(follower_id, followee_id) |
| post | id, user_id, type(VIDEO/IMAGE), title, description, cover_url, video_url, images(JSON), status(PROCESSING/PUBLISHED/FAILED), like_count, comment_count, created_at | like_count/comment_count 为冗余计数 |
| post_like | id, post_id, user_id, created_at | 唯一(post_id, user_id) |
| comment | id, post_id, user_id, content, like_count, created_at | |
| comment_like | id, comment_id, user_id, created_at | 唯一(comment_id, user_id) |

Redis Key 设计：

- `sms:code:{phone}` — 验证码，5 分钟过期
- `feed:home` — 首页信息流缓存（分页游标）
- `like:pending` — 点赞计数缓冲（定时/触发落库）
- `user:profile:{id}` — 用户主页缓存

设计约定：

- 点赞/评论数冗余在 post/comment 上，列表页零聚合查询
- 视频发布走状态机：PROCESSING → PUBLISHED / FAILED，由转码 Worker 驱动
- 分页统一游标方式（created_at + id），避免深分页

## 7. API 概览（V1）

认证：`POST /api/auth/sms-code`（发送验证码）、`POST /api/auth/login`、`POST /api/auth/refresh`
用户：`GET /api/user/me`、`GET /api/user/{id}`、`PUT /api/user/me`、`GET /api/user/{id}/posts`
关注：`POST /api/follow/{userId}`、`DELETE /api/follow/{userId}`、`GET /api/user/{id}/followers|following`
内容：`POST /api/post`（创建）、`GET /api/post/feed`（推荐流，游标分页）、`GET /api/post/{id}`、`DELETE /api/post/{id}`
上传：`POST /api/upload/presign`（预签名直传 OSS）
互动：`POST /api/post/{id}/like`、`DELETE /api/post/{id}/like`；`GET /api/post/{id}/comments`、`POST /api/post/{id}/comments`、`DELETE /api/comments/{id}`、`POST /api/comments/{id}/like`

## 8. 性能与流畅性方案

- 前端：图片懒加载、视频预加载（当前页播放/上下页预载）、骨架屏、评论列表虚拟滚动、静态资源 CDN
- 后端：Redis 缓存信息流与主页；点赞/评论数先写 Redis 再异步落库；RabbitMQ 削峰；联合索引（post 按 created_at desc、评论按 post_id+created_at）
- 视频：上传后异步转码（统一 H.264 + 封面抽帧），OSS + CDN 分发，播放器按需加载

## 9. 安全方案

- 短信验证码：5 分钟过期、60 秒重发间隔、每小时每手机号限次（防轰炸）
- 接口限流：登录、发码、发布等接口
- JWT 登录态 + 刷新机制；越权校验（只能删除自己的作品/评论）
- 发布内容基础敏感词过滤；生产环境强制 HTTPS；SQL 注入/CSRF 按框架规范防护

## 10. 部署方案

- Docker Compose 一键部署：`app`、`nginx`、`mysql`、`redis`、`rabbitmq`、`minio`、`transcode-worker`
- 云服务器：阿里云轻量 2核4G（推荐），带宽 3–5M，Ubuntu 24.04
- 上线切换：阿里云短信（需实名 + 签名/模板审核 + 备案域名）、OSS + CDN
- 域名 + HTTPS（certbot）；国内域名需 ICP 备案（约 1–2 周，提前办理）
- 可选 CI：GitHub Actions（编译 + 测试 + 镜像）

## 11. 里程碑排期（约 5–6 周）

| 里程碑 | 内容 | 预计 |
| --- | --- | --- |
| M1 | 后端地基：工程骨架、建表、验证码登录、统一响应/异常 | 1 周 |
| M2 | 内容发布：上传、视频转码链路（MQ+FFmpeg）、状态机 | 1 周 |
| M3 | 信息流 + 互动：推荐流、点赞评论、计数缓冲 | 1 周 |
| M4 | 用户体系：主页、编辑资料、关注 | 4–5 天 |
| M5 | 前端全页面：登录、首页流（手机/PC）、发布、评论、主页 | 1.5–2 周 |
| M6 | 联调打磨：全流程、空状态、响应式细节、性能 | 1 周 |
| M7 | 部署上线：Docker、域名、HTTPS、真实短信/OSS | 2–3 天 |

## 12. 风险与应对

- Spring Boot 4 → 3.5.16 已切换并验证编译通过（2026-08-28）
- 个人实名申请短信签名需备案域名：开发期用 Mock 验证码，备案完成后再切换，接口层已做 Provider 隔离
- 2核4G 转码吃 CPU：限制视频大小（≤300MB）、队列串行、必要时转码任务放本地定时执行
- MinIO → 阿里云 OSS 切换：通过统一存储接口抽象，切换只改配置
