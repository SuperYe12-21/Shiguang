# 拾光（Shiguang）实现计划

- 来源：`docs/superpowers/specs/2026-08-28-shiguang-design.md`（已获用户确认）
- 分支策略：开发一律在 `dev` 分支进行；每个里程碑完成后在代码审查 + 演示 checkpoint 通过后合并回 `main`。不在 main 上直接开发。
- 验证原则：每个任务都有明确的验证方式（编译 / 单测 / 接口冒烟 / 页面演示），不能跳过。

## M1 后端地基（约 1 周）

任务清单（按序执行，每项完成即验证）：

1. 整理工程骨架
   - 包结构：`com.shiguang.{auth,user,content,feed,interaction,common,config}`
   - pom 补齐依赖：mybatis-plus、mysql-connector-j、data-redis、amqp、validation、jjwt、springdoc-openapi、lombok
   - 验证：`mvn compile` 通过
2. 环境配置与本地依赖
   - `application.yml`（dev）：datasource / redis / rabbitmq / minio / jwt 密钥（全部走环境变量，不写死）
   - `docker-compose.dev.yml`：mysql8、redis、rabbitmq、minio 一键起
   - SQL 初始化脚本：7 张业务表 + 索引（user/follow/post/post_like/comment/comment_like）
   - 验证：compose 起服务，`mvn spring-boot:run` 启动无报错，表结构可查
3. 基础设施
   - 统一响应体 `R<T>`、统一异常处理（业务异常/参数校验/兜底 500）
   - 全局请求日志 + traceId；JWT 认证过滤器 + 白名单（登录/发码接口）
   - 验证：MockMvc 单测覆盖成功/失败路径
4. 验证码登录模块（auth）
   - `SmsProvider` 接口 + `MockSmsProvider`（日志输出验证码，固定演示码可选）+ 阿里云实现预留
   - 发送接口：60s 重发间隔、每小时每号限次（Redis 计数）、验证码 5 分钟过期
   - 登录接口：校验验证码 → 签发 JWT（access + refresh）→ 返回用户信息
   - 验证：单测 + curl 冒烟（发码→登录→`/api/user/me`）
5. 用户模块（user）
   - 用户 CRUD 基础：注册时创建用户、`GET /api/user/me`、`PUT /api/user/me`（头像/昵称/简介）
   - 验证：接口冒烟 + 单测
6. M1 checkpoint：给用户演示登录注册全流程（浏览器/接口），确认后合并 `dev` → `main`，打 tag `v0.1`

## M2 内容发布（约 1 周）

1. 存储抽象：`StorageService` 接口 + MinIO 实现（预签名直传 URL），上传接口 `POST /api/upload/presign`
2. post 创建接口：类型（视频/图文）、标题描述、封面/文件 URL、状态 PROCESSING
3. 转码链路：发布视频 → 发 RabbitMQ 消息 → 转码 Worker（FFmpeg 统一 H.264 + 封面抽帧）→ 回调更新状态 PUBLISHED / FAILED
4. 图文发布不走转码，直接 PUBLISHED
5. 作品详情/删除接口（只能删自己的）
6. 验证：单测状态机；本地起 worker 传真实视频文件，确认封面和播放 URL 生成
7. M2 checkpoint：演示发布视频全流程

## M3 信息流 + 互动（约 1 周）

1. 推荐流接口 `GET /api/post/feed`：时间倒序 + 轻量热度（点赞数加权），游标分页，Redis 缓存首页
2. 点赞：作品点赞/取消（Redis 记录 + 计数缓冲，异步落库到 post.like_count）
3. 评论：列表（游标分页）、发表、删除自己的、评论点赞/取消（同计数缓冲）
4. 主页作品列表 `GET /api/user/{id}/posts`
5. 验证：并发点赞计数一致性测试（压力脚本）；接口冒烟
6. M3 checkpoint：演示信息流、点赞、评论

## M4 用户体系（约 4–5 天）

1. 关注/取关 `POST|DELETE /api/follow/{userId}`（防自关注、唯一约束）
2. 关注/粉丝列表、粉丝/关注数（计数冗余或 Redis 计数）
3. 主页信息聚合：用户资料 + 统计 + 作品列表
4. 验证：关注关系冒烟 + 单测
5. M4 checkpoint：演示他人主页 + 关注流程

## M5 前端全页面（约 1.5–2 周）

1. 前端工程：Vite + Vue3 + Pinia + Vue Router + Element Plus（PC）+ 自研移动组件；设计令牌（米白/珊瑚色/圆角）落地为 CSS 变量
2. 登录/注册页（手机号+验证码，含倒计时、错误态）
3. 首页信息流：移动端全屏竖屏流（右侧互动栏 + 底部导航）、PC 三栏（窄图标导航 + 毛玻璃评论面板），图片懒加载、视频预加载、骨架屏
4. 发布页：视频/图片选择、标题描述、上传进度、发布状态反馈
5. 评论面板：列表 + 点赞 + 输入发送（发送按钮右置）
6. 个人主页/他人主页：头像简介、统计、作品网格、编辑资料、关注按钮
7. 响应式适配 + 空状态/加载态/错误态全覆盖
8. M5 checkpoint：手机 + PC 全流程演示

## M6 联调打磨（约 1 周）

1. 前后端全流程联调（登录→发布→刷流→点赞评论→主页）
2. 细节打磨：动画、过渡、无网/弱网态、性能（懒加载命中率、首屏耗时）
3. 补充测试：关键接口回归、边界用例
4. M6 checkpoint：完整产品演示

## M7 部署上线（约 2–3 天）

1. 生产 `docker-compose.yml`：app、nginx、mysql、redis、rabbitmq、minio、transcode-worker
2. Nginx：静态资源 + /api 反代 + HTTPS（certbot 预留）
3. 环境变量与密钥管理（.env 不入库）
4. 上线切换：阿里云短信 Provider（需备案域名）、OSS（可选，MinIO 可先顶）
5. 验证：云服务器一键部署，手机/PC 公网访问全流程
6. M7 checkpoint：上线验收

## 风险提示

- 个人实名短信签名需备案域名：开发期用 Mock 验证码，备案完成再切换，接口已隔离
- 2核4G 转码吃 CPU：视频 ≤300MB、队列串行处理
- git 推送已配置代理与凭据；密钥一律环境变量，严禁提交
