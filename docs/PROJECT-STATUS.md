# Link-Life 项目状态与路线图（会话恢复用）

> **新会话必读**：本文件是跨会话的进度记录。恢复上下文时按顺序读：
> 1. 本文件 → 2. `docs/superpowers/specs/2026-09-16-link-life-design.md`（设计文档）→ 3. `docs/superpowers/plans/`（各阶段实施计划）。
> 每完成一个阶段，更新本文件第 4 节状态和第 6 节"下一步"。

## 1. 项目目标（Goal）

Link-Life：面向**家庭、朋友间**的轻协作平台。核心场景：发起"点单"（非真实交易）→ 生成菜品清单 → 分享好友认领 → 后续引入 AI：详细菜谱、口感反馈迭代菜谱、自定义调味/食材/命名、烹饪分步趣味计时（类 Keep）、过程拍照 AI 分析（如调料多少）。

- **载体**：微信小程序 + Web 网页端（"厚后端 + 薄双端"：AI 调用、Skill、MCP、推送、内容生成统一收口在后端，双端只调同一套 HTTPS API）
- **约束**：单台 2c4G 云服务器、小范围使用、功能逐步迭代、个人主体小程序（多项平台限制已纳入设计，见 spec）
- **仓库**：https://github.com/kting-create/link-life（私有，SSH remote，主分支 `main`）

## 2. 技术栈与架构（已定，勿轻易改动）

| 项 | 选型 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.3.4 + MyBatis-Plus 3.5.7 + Flyway，模块化单体（com.linklife 下按 user/circle/auth/common/ai 分包） |
| AI | Spring AI 1.0.0（deepseek starter），所有 AI 能力收口于 `AiGatewayService`，调用写 `ai_call_log` |
| 前端 | 微信小程序原生 + Vue 3 Web（P1 建 `miniapp/`、`web/` 目录） |
| 数据 | MySQL 8（Flyway 管理 schema）、Caffeine 进程内缓存（**不上 Redis**） |
| 测试 | JUnit 5 + MockMvc + Testcontainers MySQL（单例容器模式，基类 `IntegrationTestBase`；本机 Docker Desktop 需 `server/src/test/resources/docker-java.properties`） |
| 部署 | Docker Compose（nginx + app + mysql），`deploy/` 目录；JVM -Xmx768m |
| 鉴权 | JWT（jjwt 0.12.6，access 7d / refresh 30d），`JwtAuthFilter` + `UserContext` |

**关键决策记录**：
- 个人主体限制 → 无 URL Link 短链；Web 登录用**绑定码**方案（小程序生成 6 位码，Web 输入绑定）；分享仅走小程序卡片 + Web 普通 https 链接
- 绑定码一次性用**原子条件 UPDATE** 保证（防并发 TOCTOU）
- DEEPSEEK_API_KEY 缺失时 compose 注入占位符保证开箱可启动，AI 调用失败记 ai_call_log
- 统一响应 `Result<T>` {"code":0,...}；错误码表见 `common/exception/ErrorCode.java`

## 3. 常用命令

```bash
# 后端测试（须 clean，本机需 Docker）
cd server && mvn clean test
# 本地起完整栈（deploy/.env 需要 JWT_SECRET，见 README）
cd deploy && docker compose up -d && curl http://localhost/api/health
# 备份（服务器 cron）
deploy/backup.sh
```

## 4. 路线图进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 基础框架 | 工程骨架、用户/圈子、JWT 鉴权、AI 网关骨架、Docker Compose 部署 | ✅ **已完成并合入 main（2026-09-16，24 测试全绿）** |
| P1 点单清单 MVP | 点单/清单/认领/分享、小程序端 + Web 端壳 | ⬜ **下一步** |
| P2 推送 | 微信订阅消息 + 飞书/Bark webhook、事件驱动（NotificationService 多通道） | ⬜ |
| P3 AI 菜谱引擎 | 菜谱生成、口感反馈迭代、自定义调味/食材/命名、菜谱版本化（挂 AiGatewayService） | ⬜ |
| P4 烹饪引导 | 分步趣味计时、过程拍照上传、AI 视觉分析反馈（多模态） | ⬜ |
| P5 打磨 | 口味画像沉淀、UI 打磨、性能优化 | ⬜ |

## 5. 已知延后项（P1 起择机处理）

- `wxLogin` 并发首次注册竞争：DuplicateKeyException → 500（唯一键兜底，建议 catch 后 re-select）
- `/api/auth/bind` 无限流（6 位码 10 分钟窗口，暴力破解面）；邀请码端点同理
- 邀请码碰撞无重试（概率 ~1e-13）；`uk_unionid`/`uk_code` 实为普通 KEY（命名误导）
- `JwtService` 用默认 charset 取 secret 字节（应 UTF_8）；AiGatewayService 未记 prompt/completion tokens、userId 恒 null
- refresh-token 吊销机制未设计；Caffeine 缓存决策未落地
- 引入 `UserService` 解除 user↔auth 包循环依赖（P1 加 order 模块前做）
- JWT_SECRET 生产 fail-fast（当前仅 compose 层要求）；backup.sh 路径/密码硬编码
- P1 前端启动后需填 `web-dist/`（nginx 已挂载）与小程序 appid 配置

## 6. 下一步（进入新会话时从这里继续）

**P1 计划尚未编写**。流程：用 brainstorming 细化 P1 范围（清单数据模型、share_token 分享、认领状态机、小程序/Web 端壳）→ writing-plans 写实施计划 → subagent-driven-development 执行。设计输入：spec 第 4 节（点单清单模块）已定稿的数据模型与分享机制。

---

*最后更新：2026-09-16（P0 完成合并）*
