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
| P1 点单清单 MVP | 点单/清单/认领/分享、小程序端 + Web 端壳 | ✅ **已完成（2026-09-17，分支审查后合并）** |
| P2 推送 | 站内通知 + 事件驱动多通道（飞书 Webhook、微信订阅消息，config 门控默认关）+ Web/小程序通知页 | ✅ **已完成并合入 main（2026-09-17，PR #2，51 测试全绿）** |
| P3 AI 菜谱引擎 | 菜谱生成、口感反馈迭代、自定义调味/食材/命名、菜谱版本化（挂 AiGatewayService） | ⬜ |
| P4 烹饪引导 | 分步趣味计时、过程拍照上传、AI 视觉分析反馈（多模态） | ⬜ |
| P5 打磨 | 口味画像沉淀、UI 打磨、性能优化 | ⬜ |

## 5. 已知延后项（P1 起择机处理）

- `wxLogin` 并发首次注册竞争：DuplicateKeyException → 500（唯一键兜底，建议 catch 后 re-select）
- 邀请码碰撞无重试（概率 ~1e-13）；`uk_unionid`/`uk_code` 实为普通 KEY（命名误导）
- `JwtService` 用默认 charset 取 secret 字节（应 UTF_8）；AiGatewayService 未记 prompt/completion tokens、userId 恒 null
- refresh-token 吊销机制未设计；Caffeine 缓存决策未落地
- JWT_SECRET 生产 fail-fast（当前仅 compose 层要求）；backup.sh 路径/密码硬编码
- P1 前端启动后需填 `web-dist/`（nginx 已挂载）与小程序 appid 配置
- P2：Bark/Server酱通道延后、小程序订阅授权埋点延后（等模板开通）

## 6. 下一步（进入新会话时从这里继续）

**全量待办清单在 `docs/TODO.md`**（含 P1~P5 所有任务 + 🧑 标记的需本人线下办理事项，做完勾选）。新会话：读本文件 → 读 TODO.md 第一个未勾选项 → 继续执行。

P1（点单清单 MVP）已完成并经 PR #1 合入 main，且已通过**本地全链路手工验证**（小程序测试号登录、建圈、点单、认领/收单状态机、绑定码登录 Web、Web/小程序分享落地、限流 429）。验证期间修复：WXML `wx:else` 兼容写法、小程序 BASE_URL 走 nginx 80 代理、`jscode2session` text/plain 响应解析（67ed235）。

本地验证环境：`deploy/.env` 已配小程序**测试号** WX_APPID/WX_SECRET（正式注册后替换）；启动 `cd deploy && docker compose up -d --build`，入口 `http://localhost`。

P2（推送模块）已完成并经 PR #2 合入 main：Flyway V3 站内通知表 → `NotificationService` + Spring Event 多通道（飞书 Webhook、微信订阅消息，config 门控默认关、缺配置静默降级）→ Web 端通知页 + 未读角标轮询 → 小程序端通知页。全分支终审通过（修复：通知 title 列扩为 VARCHAR(255)+截断兜底+逐收件人容错、Web 错误处理、通道日志带堆栈），51 测试全绿。🧑 延后：飞书群 Webhook 配置后验证、微信订阅模板开通后单独小迭代（订阅授权埋点 + 真机验证，见 TODO.md 随手记录区）。

下一步进入 **P3 AI 菜谱引擎**：recipe 表版本化 → AiGatewayService 结构化输出生成菜谱 → 口感反馈迭代闭环 → 自定义调味/食材/命名 → 流式输出。设计输入：spec 第 7 节；🧑 前置：DeepSeek API Key 充值配置（见 TODO.md 第 3 节）。

---

*最后更新：2026-09-17（P2 合入 main，API 级验证中）*
