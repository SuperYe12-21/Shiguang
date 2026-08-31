# 拾光（Shiguang）

清新明亮的短视频 & 图文分享社交平台（求职作品）。抖音式竖屏信息流，支持手机号验证码登录、视频/图文发布、FFmpeg 转码、点赞评论（规划中）等功能。

## 技术栈

- 后端：Java 21 · Spring Boot 3.5 · Maven · MyBatis-Plus 3.5 · Spring Security + JWT
- 中间件：MySQL 8 · Redis（验证码限流/缓存）· RabbitMQ（转码任务队列）· MinIO（对象存储，S3 兼容）
- 前端：Vue 3 + Vite + Pinia（M5 起）

## 项目结构

```
db/init/          SQL 初始化脚本（001 全量 / 002 增量）
src/main/java/com/shiguang/
  auth/           验证码登录、JWT
  user/           用户模块
  content/        作品发布 + 转码状态机
  storage/        存储抽象（MinIO 预签名直传）
  common/         统一响应、异常、traceId
  config/         Security / MyBatis-Plus / OpenAPI
src/test/java/    TDD 单元测试 + 接口集成测试
docs/superpowers/ 设计与实现计划
```

## 本地开发

依赖：MySQL 8（库 `shiguang`）、Redis、MinIO、RabbitMQ、FFmpeg。

```powershell
# 0. 一键启动全部（中间件 + 后端 8080 + 前端 5173，自动打开浏览器）
powershell -ExecutionPolicy Bypass -File .devtools/start-all.ps1

# 停止前后端（中间件保持运行）
powershell -ExecutionPolicy Bypass -File .devtools/start-all.ps1 -Stop
```

> 一键脚本会自动检查并启动 Redis / MinIO / RabbitMQ；MySQL 请确保已启动（Docker 或本机服务）。
> 后端通过 `maven-settings-public.xml` 使用公共 Maven 源启动（不依赖内网 Nexus）。

```powershell
# 1. 初始化数据库
mysql -uroot -p < db/init/001_schema.sql

# 2. 启动中间件（Redis / MinIO / RabbitMQ，便携版见 .devtools/start-dev.ps1）
powershell -File .devtools/start-dev.ps1

# 3. 启动后端（FFmpeg 路径按本机配置）
$env:FFMPEG_PATH = "D:\Shiguang\.devtools\ffmpeg\ffmpeg-9.0.1-essentials_build\bin\ffmpeg.exe"
mvn spring-boot:run

# 4. 打开接口文档
# http://localhost:8080/swagger-ui.html
```

开发期短信验证码为 Mock（固定 `123456`），生产可无缝切换阿里云短信 Provider。

## 里程碑

- v0.1 M1 登录注册与用户模块
- v0.2 M2 内容发布（存储直传 + FFmpeg 转码队列 + 作品详情/删除）
- 规划：M3 信息流与互动 · M4 关注体系 · M5 前端全页面 · M6 联调打磨 · M7 部署上线