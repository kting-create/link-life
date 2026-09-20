# P5 打磨与运维 设计文档

日期:2026-09-18
状态:已与需求方确认
分支:feature/p3-recipe-engine(与 P3/P4 同分支,最后统一验证一次性合并)

## 1. 范围与目标

P3/P4 功能收尾后的打磨阶段,分五块:

1. **口味画像**:双端展示(摘要 + 标签)+ AI 迭代 prompt 注入画像(与生成路径对齐)
2. **后端打磨**:Caffeine 数据缓存落地、`wxLogin` 并发 catch-reselect、refresh-token 吊销(token_version 方案)、索引修正迁移、登出 API
3. **UI 打磨**:P4/P3 终审延后项修复 + 双端轻量设计 token
4. **部署与备份**:mysql healthcheck init 等待、backup.sh 参数化 + restore.sh 演练脚本
5. **压测与安全**:小规模压测脚本 + 结果记录、安全检查清单 + 低垂果实修复

不在范围内:评分反馈即时沉淀画像(保持迭代成功才沉淀)、全量视觉重设计、对象存储备份、🧑 事项真实执行(DeepSeek 真跑/真机/备份恢复确认,统一验证阶段做;DeepSeek Key 已就位)。

## 2. 关键决策记录

- **口味画像展示程度**:仅展示 + 迭代注入,不做评分即时沉淀(避免无 AI 时画像变更规则的复杂度)。
- **refresh-token 吊销**:token_version 方案 —— `user` 表加 `token_version`,签发嵌入 `ver` claim,校验比对 DB,不一致即失效。无新表、无 Redis,重启不丢;顺带补齐缺失的登出功能(bump version 即全端失效)。
- **UI 打磨深度**:修延后项 + 轻量 token(CSS 变量/公共类),不动页面结构、不重设计;视觉回归依赖统一验证阶段手工冒烟。
- **压测延后与否**:本机 compose 环境小规模压测(ab),结果仅作基线记录;服务器真实环境压测延后到部署后。
- **`.env` 明文密钥已提交 git**:仅写入安全清单提醒(🧑 决定轮换密钥 + 是否清理历史),不代办。
- 🧑 依赖事项:DeepSeek Key 已配置 `deploy/.env`(真跑验证无阻塞,统一验证阶段执行)。

## 3. 数据模型(Flyway V6)

```sql
-- 1) refresh-token 吊销基础
ALTER TABLE `user` ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;

-- 2) 索引修正(消除命名误导)
ALTER TABLE `user` DROP INDEX uk_unionid, ADD UNIQUE INDEX uk_unionid (unionid);
--    unionid 业务上应唯一;MySQL 唯一索引允许多个 NULL(个人主体小程序多为 NULL,不受影响)。
--    历史数据若有重复需在迁移中先去重(实现时确认,当前库量小)。

ALTER TABLE binding_code DROP INDEX uk_code, ADD INDEX idx_code (code);
--    binding_code 按 code 取最新未用(ORDER BY id DESC LIMIT 1),语义保留历史码,不加唯一,仅改名。
```

仅 `user` 表新增 `token_version` 列,无新表。错码新增见 §4.6。

## 4. 后端 API 与服务

### 4.1 口味画像注入

- `RecipeGenerationService` 迭代路径:与生成路径对齐,把 `TasteProfileService.getSummary()` 拼进迭代 prompt(模板机制内新增迭代模板占位)。
- 展示:复用现有 `GET /api/me/taste-profile`,不动。

### 4.2 Caffeine 数据缓存

- `@EnableCaching` + `CaffeineCacheManager`(spec 配置,进程内)。
- 缓存:`sheetDetail`(清单详情)、`shareView`(分享只读)、`recipeDetail`(菜谱详情),TTL 5min,MaximumSize 200。
- 写操作逐出:认领/release/完成/清单状态流转 → sheetDetail+shareView;菜谱编辑/迭代/回滚/补丁确认 → recipeDetail。逐出按 key 精确,不做全清。
- 集成测试:二次读取命中缓存(可通过 SQL 计数或修改 DB 后仍读到旧值断言)、写后失效读到新值。

### 4.3 wxLogin catch-reselect

- `AuthService.wxLogin` catch `DuplicateKeyException` → 按 openid re-select 返回既有用户(参照 `RecipeService.java:163-169` 范式);并发集成测试(两线程同时首登同一 openid,均成功且为同一 userId)。

### 4.4 token_version 吊销 + 登出

- `JwtService`:签发 access/refresh 均嵌 `ver`(= 签发时 `user.tokenVersion`)。
- 校验:`JwtAuthFilter` 与 refresh 端点比对 claim vs DB 当前值,不一致 → 401 + 错误码 3007(TOKEN_REVOKED)。
- 新增 `POST /api/auth/logout`(JWT):`token_version + 1`,返回 Result.ok。双端 profile 页加登出按钮(确认弹窗 → 调用 → 清 storage → 回登录页)。
- 兼容:V6 迁移后存量 token 无 `ver` claim → 视为 ver=0,与 DEFAULT 0 一致,无需强制重登。

### 4.5 P3 Minor(后端侧)

- `RecipeService.edit` 补 `content.validate()`(ingredients/steps 非空,非法 → 5001 语义沿用)。

### 4.6 错误码

| 码 | 名称 | 场景 |
|---|---|---|
| 3007 | TOKEN_REVOKED | token_version 不匹配(登出/全端失效) |

## 5. UI 打磨(双端)

### 5.1 P4 延后项

- **CookMode 跳过语义**(小程序 + Web 同构修复):"跳过本步"直接完成本步进入下一步(调既有 finishStep 逻辑),消除 skip 后 remainSec=0 时"继续"按钮死状态。
- **Web 照片上传错误处理**:`web/src/api/recipe.js uploadPhoto` 裸 fetch 加 try/catch 与响应类型判断(网络异常/非 JSON/502→ 友好 toast,不再裸抛 SyntaxError);对齐 request.js 的 2002 刷新重试语义(或至少 401 提示重新登录)。
- **音频手势预热**:Web CookMode 在"开始烹饪"手势内创建 AudioContext 并 `resume()`;小程序 cook-mode `onLoad` 即 `createInnerAudioContext` 预创建(消除首播延迟)。

### 5.2 P3 Minor(双端)

- 小程序 `recipe-detail`:`durationSec` 为 null/0 时隐藏时长文本(对齐 Web `v-if`),不再显示"约 0 秒"。
- 双端手动编辑加 atLimit 预检查:atLimit 时"保存编辑"按钮禁用 + 提示文案(后端 5002 兜底不变)。
- Web `RecipeGenerate`:缺 `circleId/dishName` 参数时 redirect 回来源/菜谱详情,不发畸形请求。
- 双端 SSE 解析:多行 `data:` 帧按 `\n` 拼接(`web/src/api/sse.js`、`miniapp/utils/sse.js`),消除粘包隐患。

### 5.3 设计 token(轻量)

- Web:`web/src/styles/tokens.css` 定义 CSS 变量(主色 #07c160、文字主/次色、背景、边框、圆角、间距档位、错误/警告色),公共类 `.btn` `.card` `.toast`(按现有页面观感归纳);各页 scoped style 改引变量(机械替换,不改结构)。
- 小程序:`app.wxss` 定义公共类与 page 级变量(wxss 变量),页面主要颜色/圆角替换。
- 新增画像 UI、登出按钮直接用 token 风格。

### 5.4 口味画像展示

- 小程序 `profile` 页:画像卡片(摘要 + 标签胶囊;空态:"完成菜谱反馈后沉淀你的口味画像")+ 登出按钮。
- Web:新增 `Profile.vue`(路由 `/me`,导航入口;画像摘要 + 标签 + 登出)。

## 6. 部署与备份

- **mysql healthcheck**:改为区分 init 与就绪 —— `mysqladmin ping` 成功后再执行 `SELECT 1`(目标库),配 `start_period: 30s`;避免首次建库期间 `service_healthy` 提前放行导致 Flyway 连接失败。
- **backup.sh**:去硬编码(容器路径/备份目录/密码改 env 变量,默认值仅在缺省时兜底并告警);统一走 `docker exec`。
- **restore.sh**(新增):恢复 dump 到临时库 + 行数抽查校验,输出演练结果摘要;配合 `docs/backup-restore-drill.md` 演练步骤(🧑 统一验证阶段真实确认)。

## 7. 压测与安全

### 7.1 压测(小规模基线)

- `scripts/load-test.sh`:ab 打热点只读接口(分享页 `/s/{token}`、清单详情、菜谱详情;各 1000 请求 × 并发 10/50),结果记 `docs/load-test-results.md`(吞吐/p50/p99,ab 输出整理)。
- 发现明显慢点(如 N+1)顺手修,不做大重构。

### 7.2 安全清单 + 低垂果实修复

- `docs/security-checklist.md`:覆盖鉴权/越权/限流/上传/注入/密钥管理/传输,逐项标注现状与残余风险(含 `.env` 明文密钥已提交 git 的 🧑 处置项)。
- 修复:
  1. 限流扩展:`/api/auth/wx-login`(IP 维度)、`/api/auth/refresh`(IP 维度)、`/api/recipes/generate` 与 iterate(userId 维度,配额独立于 atLimit)。
  2. JWT_SECRET 生产 fail-fast:prod profile 下检测 dev 前缀/已知弱值 → 启动失败。
  3. backup.sh 密码进程列表泄漏(§6 一并修复)。

## 8. 测试策略

- 集成测试(沿 `IntegrationTestBase` + MockMvc):
  - 缓存:命中/写后失效/多 key 隔离;既有测试回归(缓存不破坏现有断言)
  - wxLogin 并发:两线程首登同一 openid → 同一 userId、无 500
  - token_version:登出后旧 access/refresh 均 3007;ver 不匹配 401;存量无 ver token 兼容
  - edit validate:空 ingredients/steps 拒绝
  - 画像注入:迭代 prompt 含 summary(单测级断言模板渲染)
  - 限流:wx-login/refresh/generate 超限 429
  - V6 迁移:Flyway 自动验证(启动即验);unionid 唯一约束生效
- 双端 UI 修复项(跳过语义/音频/上传错误/token 风格)手工验证归统一验证阶段。
- 压测与安全清单为文档产出,不走自动化。

## 9. 实施波次(供 writing-plans 细化)

1. **后端波**:V6 迁移 + 画像注入 + 缓存 + wxLogin + token_version/登出 + edit validate + 限流扩展 + JWT fail-fast
2. **前端波**:双端画像展示 + 登出按钮 + P4/P3 延后项修复 + 设计 token 应用
3. **运维波**:healthcheck + backup/restore 脚本 + 压测 + 安全清单文档

每波:subagent 实现 + 任务级审查修复循环;每波完成 commit+push;全部完成后分支级终审 → 更新 PROJECT-STATUS/TODO → 统一验证阶段。

## 10. 风险与开放问题

- 缓存引入可能让既有集成测试读到脏缓存:逐出粒度要细,测试基类必要时逐用例清缓存(实现时定)。
- token_version 校验在每个请求都查 DB(现 JwtAuthFilter 已查 user?实现时确认,若已查则零额外成本;否则需权衡)。小规模使用可接受。
- unionid 唯一化迁移遇重复数据:当前本地/生产库量极小,迁移前去重兜底。
- ab 对 HTTPS/动态 token 支持弱:分享页无需鉴权可直测;带鉴权接口用 `-H` 传固定 token,若 ab 不便则换 curl 循环或 Python 小脚本(实现时定)。
