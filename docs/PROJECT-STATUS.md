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
| P3 AI 菜谱引擎 | 菜谱生成、口感反馈迭代、自定义调味/食材/命名、菜谱版本化（挂 AiGatewayService） | ✅ **P3 开发完成（2026-09-18，分支已推送 origin，PR 未建）** |
| P4 烹饪引导 | 分步趣味计时、过程拍照上传、AI 视觉分析反馈（多模态） | ✅ **P4 开发完成且分支级终审通过（2026-09-18，1 轮修复波，105 测试全绿）** |
| P5 打磨 | 口味画像沉淀、UI 打磨、性能优化 | ✅ **P5 开发完成且分支级终审通过（2026-09-20，1 轮修复波 + 终审修复波，126 测试全绿）** |

## 5. 已知延后项（P1 起择机处理）

- ~~`wxLogin` 并发首次注册竞争~~ ✅ P5 已修（catch-reselect，eb88256）
- ~~`uk_unionid`/`uk_code` 索引命名误导~~ ✅ P5 已修（V6：unionid 真唯一 + idx_code 改名）；**部署前置**：V6 上线前须确认 user 表无重复非 NULL unionid（见 docs/security-checklist.md 部署前置检查）
- ~~refresh-token 吊销机制未设计；Caffeine 缓存决策未落地~~ ✅ P5 已落地（token_version + ver claim + logout API；Caffeine 缓存 sheetDetail/shareView/recipeDetail TTL 5min）
- ~~JWT_SECRET 生产 fail-fast；backup.sh 路径/密码硬编码~~ ✅ P5 已修（prod profile fail-fast；backup.sh 参数化 + MYSQL_PWD 传密码 + restore.sh 演练脚本）
- ~~`wxLogin` 并发注册、`RecipeService.edit` 未调 `content.validate()`、小程序编辑无 atLimit 预检查、durationSec 显示"0 秒"、Web /recipes/generate 无守卫、SSE 多行帧拼接~~ ✅ P5 已修
- 邀请码碰撞无重试（概率 ~1e-13）
- `JwtService` 用默认 charset 取 secret 字节
- P1 前端启动后需填 `web-dist/`（nginx 已挂载）与小程序 appid 配置
- P2：Bark/Server酱通道延后、小程序订阅授权埋点延后（等模板开通）；双端通知 UI 手工验证延后（单机环境无第二设备，API 级验证已覆盖核心链路）
- 🧑 P3 双端真机流式验证延后（需微信开发者工具真机预览，enableChunked 需基础库 ≥2.20.1；API 级 SSE 已有测试覆盖）
- P3 关键裁决记录（实现依据）：spec §1"手动编辑全做"优先于 §7 UI 清单（双端已补编辑入口）；SSE emitter 完成时机必须在 Flux 终态回调（runAsync finally-complete 会截断真实异步流）；`IterationResult` 需 `@JsonProperty("taste_summary")`（AI snake_case 键映射）
- P5 终审延后 Minor（backlog，不阻塞合并）：JwtAuthFilter 每请求 getUserById 热路径查询（可加短 TTL 缓存）；缓存逐出在事务提交前（回填竞态窗口小，TTL 兜底）；手动编辑 5008 已修（4xx 语义落地）；`contains("prod")` 子串匹配（fail-safe 方向，建议后续注入 Environment 精确匹配）；最后一步 skip/继续无视觉反馈；logout 双重 reLaunch（幂等）；RecipeGenerate 取参不一致/redirect 前闪烁；web 双主色共存（#4f7cff 蓝 vs #07c160 绿，统一需产品决策）；缓存逐出改精确 token 后 SheetDetailVO 内昵称变更仅 TTL 兜底；healthcheck/backup.sh 特殊字符密码未加引号场景（已加引号修复主要路径）；T15 文档小瑕疵

## 6. 下一步（进入新会话时从这里继续）

**全量待办清单在 `docs/TODO.md`**（含 P1~P5 所有任务 + 🧑 标记的需本人线下办理事项，做完勾选）。新会话：读本文件 → 读 TODO.md 第一个未勾选项 → 继续执行。

P1（点单清单 MVP）已完成并经 PR #1 合入 main，且已通过**本地全链路手工验证**（小程序测试号登录、建圈、点单、认领/收单状态机、绑定码登录 Web、Web/小程序分享落地、限流 429）。验证期间修复：WXML `wx:else` 兼容写法、小程序 BASE_URL 走 nginx 80 代理、`jscode2session` text/plain 响应解析（67ed235）。

本地验证环境：`deploy/.env` 已配小程序**测试号** WX_APPID/WX_SECRET（正式注册后替换）；启动 `cd deploy && docker compose up -d --build`，入口 `http://localhost`。

P2（推送模块）已完成并经 PR #2 合入 main：Flyway V3 站内通知表 → `NotificationService` + Spring Event 多通道（飞书 Webhook、微信订阅消息，config 门控默认关、缺配置静默降级）→ Web 端通知页 + 未读角标轮询 → 小程序端通知页。全分支终审通过（修复：通知 title 列扩为 VARCHAR(255)+截断兜底+逐收件人容错、Web 错误处理、通道日志带堆栈），51 测试全绿。**API 级验证已通过**（4 事件/自认领/长标题溢出/越权 3006/游标分页/已读幂等/通道故障隔离，compose 冒烟含降级路径与飞书错误码隔离）；双端 UI 手工验证延后（单机环境，见第 5 节）。🧑 延后：飞书群 Webhook 配置后真跑验证、微信订阅模板开通后单独小迭代（订阅授权埋点 + 真机验证，见 TODO.md 随手记录区）。

**P3（AI 菜谱引擎）开发完成（2026-09-18，feature/p3-recipe-engine 分支，已推送 origin，PR 未建）**：Flyway V4 菜谱表（recipe + recipe_version 版本化，指针回滚、上限 5 版）→ 调料架/食材柜 CRUD（生成时注入约束）→ 口感画像（taste-profile，反馈沉淀 summary/tags）→ `AiGatewayService` 结构化输出生成菜谱（JSON content schema，解析失败 error 5004）→ 反馈评分 + AI 迭代新版本（change_note 记录）→ 手动编辑（MANUAL_EDIT）/回滚/版本列表 → SSE 流式生成/迭代（打字机推进，nginx 不缓冲）→ 提示词模板收口 → 小程序生成/详情/调料架页 + Web 菜谱视图。`AiGatewayService` 已补 tokens 统计与真实 userId。**全分支终审已通过（1 轮修复波：change_note 255 溢出、流中断前端挂死+90s 超时 error 事件、双端手动编辑 UI 补齐、Web 详情页错误处理、openRecipe 仅 5001 进生成页）**，85 测试全绿，Web 构建通过。

**用户决策（2026-09-18）：P4、P5 与 P3 不分批合并——在同一分支（feature/p3-recipe-engine）上把后续功能全部做完，最后统一验证、一次性合并。** 因此 P3 的 PR 暂不创建。

**下一步（按序）**：
1. ~~P5（打磨）~~ ✅ 已完成（2026-09-20，见下）。
2. **统一验证**：`cd deploy && docker compose up -d --build` 全链路 compose 冒烟（P1~P5 所有功能）→ 按 `scripts/recipe-sample-validation.md` 真跑小样本（DeepSeek Key 已配 deploy/.env）→ 🧑 小程序真机验证（流式 + 烹饪模式）→ 🧑 服务器备份恢复演练确认（deploy/restore.sh）→ 一个 PR 合并全部。部署到生产前执行 security-checklist.md 部署前置检查（unionid 去重）。

**P4（烹饪引导）已完成且分支级终审通过（2026-09-18，feature/p3-recipe-engine 分支，105 测试全绿、Web 构建通过、compose 冒烟通过）**：Flyway V5 recipe_photo 表 + 6xxx 错误码段 → 双 ChatModel 基建（spring-ai-starter-model-openai 指向百炼 OpenAI 兼容端点，`spring.ai.chat.client.enabled=false` + 手动 ChatClient bean，AiGatewayService 新增 callStructuredWithImage，任何失败归 6005）→ 照片上传/列表/删除（本地卷 /data 根语义、魔数校验、路径遍历防护、50 张上限、multipart 超限 6002）→ Qwen3-VL 视觉分析（提示词含菜名/步骤/口味画像，结果存 photo.analysis）→ 补丁确认应用（changes 合并 → source=PHOTO_ANALYSIS 新版本，change_note=advice 截 255）→ 小程序烹饪模式页（计时/亮屏常亮/提示音 ding.wav/手势滑动/拍照/分析确认）+ Web CookMode 页（Web Audio 提示音）。分支级终审 1 轮修复波：multipart 超限 6002 handler、6007/6003 集成断言、小程序手势滑动。🧑 DASHSCOPE_API_KEY（阿里百炼）待配置到 deploy/.env（缺省注入占位 key，视觉分析 6005 降级；**不可显式置空**——空 key 会导致启动失败）。

**P5（打磨与运维）已完成且分支级终审通过（2026-09-20，feature/p3-recipe-engine 分支，126 测试全绿、Web 构建通过、已推送 origin d0eaa4b）**：Flyway V6（user.token_version + unionid 真唯一 + binding_code idx_code 改名）→ token_version 吊销机制（JWT ver claim，filter/refresh 双校验，错误码 3007，存量 token 兼容）+ `POST /api/auth/logout` + 双端登出按钮（App.vue 顶栏与 Profile 页均走服务端吊销）→ wxLogin 并发 catch-reselect（移除 @Transactional 的 MVCC 裁决）→ Caffeine 缓存（sheetDetail/shareView/recipeDetail，TTL 5min，写路径精确逐出）→ AI 迭代 prompt 注入口味画像 → 双端口味画像展示（小程序 profile 卡片 + Web /me Profile 页）→ UI 延后项修复（CookMode 跳过/继续语义、音频手势预热、Web 上传错误处理、atLimit 预检查、durationSec 空值、SSE 多行帧）+ 双端轻量设计 token → 限流扩展（wx-login/refresh 按 IP、AI 生成/迭代按用户）→ JWT_SECRET prod fail-fast（compose SPRING_PROFILES_ACTIVE=prod）→ 手动编辑非法 content 5008/400（AI 链路保持 5004）→ mysql healthcheck 区分 init 与就绪（start_period + SELECT 1）→ backup.sh 参数化 + restore.sh 演练脚本 + docs/backup-restore-drill.md → 压测基线（scripts/load-test.sh + docs/load-test-results.md，share_view c50≈9656 RPS 无明显慢点）+ docs/security-checklist.md（含部署前置：unionid 去重检查）。**分支级终审（P5 范围 3959a68..4ba08f1）有条件通过，修复波（I-1 登出统一、I-2 编辑 5008、4 项随版）复审 7/7 ADDRESSED。** `.env` 经 git 全史核查从未提交（此前记录有误）；DeepSeek Key 已配 deploy/.env。

---

*最后更新：2026-09-20（P5 开发完成且分支级终审通过（1 轮任务级修复波 + 1 轮终审修复波），126 测试全绿、Web 构建通过；下一步统一验证后一个 PR 合并 P3~P5；分支已推送 origin d0eaa4b）*
