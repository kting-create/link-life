# P3 AI 菜谱引擎 设计文档

日期：2026-09-18
状态：已与需求方确认定稿
前置：主设计文档 `2026-09-16-link-life-design.md`（第 5 节 AI 网关层）、P1 spec（dish 表预留）、P2 已合入 main。

## 1. 范围与已定决策

P3 交付：菜谱生成（结构化输出）、口感反馈迭代闭环、自定义能力（调料架/手动编辑/个性化命名）、真流式输出、菜谱版本化。同时顺手修复 `ai_call_log` 的 tokens 统计与真实 userId（已知延后项）。

与需求方确认的决策：

| 决策点 | 结论 |
|---|---|
| 流式方案 | 真流式 SSE，双端都吃流（Web 用 fetch ReadableStream，小程序用 wx.request enableChunked） |
| 流式内容形态 | 方案 A：AI 原始文本增量流（delta），流结束后后端结构化解析落库再发 done |
| 菜谱挂载 | 生成时按需 upsert dish 并回填 recipe_id，圈内同名共享一份菜谱 |
| 反馈闭环 | 星级（1~5）+ 文字评价，手动触发迭代；版本上限 5；taste_prefs 由 AI 在迭代时一并沉淀 |
| 自定义能力 | 调料架/食材库 + 菜谱手动编辑（存新版本）+ 个性化命名，全做 |
| LLM | DeepSeek，模型 `deepseek-flash`（DEEPSEEK_MODEL 可配）；API Key 由需求方配置在 deploy/.env |
| 真实验证 | 开发期 mock；交付小样本验证脚本，Key 就绪后真跑 3~5 道菜调 prompt |

## 2. 数据模型（Flyway V4）

```sql
-- 菜谱档案：一道 dish 一条 recipe，dish.recipe_id 回填指向它
CREATE TABLE recipe (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dish_id BIGINT NOT NULL,
    custom_name VARCHAR(64) NULL COMMENT '个性化命名，空则显示 dish.name',
    current_version INT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_dish (dish_id)
);

-- 版本表：生成/迭代/手动编辑都插新行，历史行不可变
CREATE TABLE recipe_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    version INT NOT NULL COMMENT '从 1 递增',
    source VARCHAR(16) NOT NULL COMMENT 'AI_GENERATE / AI_ITERATE / MANUAL_EDIT',
    content JSON NOT NULL COMMENT '结构化菜谱，schema 见 2.1',
    change_note VARCHAR(255) NULL COMMENT '迭代原因/编辑说明',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_recipe_version (recipe_id, version)
);

-- 口感反馈：迭代闭环的原始数据
CREATE TABLE recipe_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score TINYINT NOT NULL COMMENT '1~5',
    comment VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_recipe (recipe_id)
);

-- 调料架/食材库：个人维度，生成菜谱时注入 prompt 约束
CREATE TABLE pantry_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL COMMENT 'SEASONING / INGREDIENT',
    name VARCHAR(64) NOT NULL,
    note VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_name (user_id, name)
);
```

V4 同时对 V2 已有的 `dish` 表补唯一键：`UNIQUE KEY uk_circle_name (circle_id, name)`（dish 表当前无数据，安全），支撑并发 upsert。

### 2.1 content JSON schema

AI 结构化输出与 `recipe_version.content` 共用同一结构：

```json
{
  "servings": 2,
  "total_minutes": 40,
  "ingredients": [ { "name": "五花肉", "amount": "500g" } ],
  "seasonings": [ { "name": "生抽", "amount": "2勺" } ],
  "steps": [ { "no": 1, "text": "冷水下锅焯水…", "duration_sec": 300 } ],
  "tips": "…"
}
```

### 2.2 关键决策

- **版本指针而非复制**：回滚 = 修改 `recipe.current_version` 指针，不插新行。
- **版本上限 5**（AI 迭代 + 手动编辑合计）：达到上限后再迭代/编辑返回 RECIPE_VERSION_LIMIT，提示先回滚。
- **taste_prefs 复用 V1 已有列** `user_profile.taste_prefs`（JSON），结构：`{"summary": "偏清淡、忌辣", "tags": ["清淡","忌辣"]}`；迭代时由 AI 同调用输出，覆盖式更新。
- **dish upsert**：生成入口按 `(circle_id, name)` 查 dish，无则插入；insert 并发冲突 catch DuplicateKeyException 后 re-select。
- **权限**：菜谱随 dish 挂 circle，圈内成员可查看/编辑/反馈/迭代（与 P1 认领一致的信任模型）；圈外访问按不存在处理。调料架纯个人。
- **错误码 5xxx 段**：RECIPE_NOT_FOUND(5001)、RECIPE_VERSION_LIMIT(5002)、RECIPE_AI_FAILED(5003)、RECIPE_PARSE_FAILED(5004)、PANTRY_ITEM_EXISTS(5005)、RECIPE_ALREADY_EXISTS(5006)、PANTRY_ITEM_NOT_FOUND(5007)。

## 3. API 设计

### 3.1 菜谱

```
POST /api/recipes/generate            ← SSE 流式，生成首版
     body {circleId, dishName}
POST /api/recipes/{id}/iterate        ← SSE 流式，反馈迭代新版本
     body {comment}（评分经 feedback 接口单独提交；迭代时取该 recipe 最近反馈喂 AI）
POST /api/recipes/{id}/feedback       ← 提交评价 {score, comment}，不触发 AI
GET  /api/recipes/{id}                ← 当前版本详情（含 custom_name、版本元信息列表）
GET  /api/recipes/by-dish             ← ?circleId=&dishName= 按圈内菜名查菜谱详情，未建返回 5001（双端 item 入口判断"查看/生成"用）
GET  /api/recipes/{id}/versions       ← 版本列表（元信息，不含 content）
GET  /api/recipes/{id}/versions/{v}   ← 某版本 content
PUT  /api/recipes/{id}                ← 手动编辑 {content?, customName?, changeNote}
                                        改 content → 插新版本（MANUAL_EDIT）；仅改 customName 不加版本
POST /api/recipes/{id}/rollback       ← {version}，仅改 current_version 指针
```

### 3.2 调料架 / 口味画像

```
GET    /api/me/pantry            ← 列表
POST   /api/me/pantry            ← {type, name, note?}，重名返回 5005
DELETE /api/me/pantry/{itemId}
GET    /api/me/taste-profile     ← 读 user_profile.taste_prefs，无则返回空对象 {}
```

### 3.3 SSE 流协议（POST + text/event-stream）

`EventSource` 原生只支持 GET，故双端均不用它：Web 用 `fetch` + `ReadableStream` 手工解析 SSE 帧；小程序用 `wx.request` + `enableChunked: true` + `onChunkReceived` 解析。线路格式为标准 SSE 帧：

```
event: delta
data: {"text": "{\"servings\""}

event: done
data: {"recipeId": 1, "version": 2}

event: error
data: {"code": 5003, "message": "AI 生成失败，请重试"}
```

- **delta**：AI 原始文本增量（JSON 文本逐段流出），前端打字机展示；收到 done 后拉 `GET /api/recipes/{id}` 渲染结构化菜谱卡。
- **落库时机**：后端聚合完整输出 → 剥围栏/解析/校验 → 才在单事务内完成全部 DB 写入（dish upsert + recipe + version，或迭代仅 version/指针/画像）→ 发 done。解析失败发 error，由于写入延迟到解析成功后，天然不留脏数据，无需回滚删除。
- **流中断**：客户端断开时取消 Flux 订阅、丢弃未落库结果，ai_call_log 记 ok=false。
- **鉴权**：SSE 请求同样经过 JwtAuthFilter（两端均可携带 Authorization header）。
- **超时**：全程 >90s 后端主动发 error 并关闭。

## 4. AI 网关扩展

```java
// 现有阻塞调用重构为带 userId（P0 骨架无其他调用方）
String call(Long userId, String scene, String prompt);

// 结构化输出：要求纯 JSON → 剥围栏 → Jackson 解析 → schema 校验
<T> T callStructured(Long userId, String scene, String prompt, Class<T> type);

// 流式：SSE 端点底层；订阅由 RecipeService 控制
Flux<String> stream(Long userId, String scene, String prompt);
```

- **tokens 统计**：阻塞路径从 CallResponse metadata 的 usage 取 promptTokens/completionTokens；流式路径从 Flux 末尾聚合 metadata 取；取不到记 null，统计失败不影响主流程。
- **解析容错**：剥 markdown 围栏 → 截取首个 `{` 到末个 `}` → Jackson 反序列化 → 必填校验（ingredients/steps 非空）；失败抛 RECIPE_PARSE_FAILED，ai_call_log 记 ok=false。
- **userId 传递**：异步流中显式传参，不依赖 ThreadLocal 上下文。

## 5. 提示词（常量收口在 ai/prompt/）

- **scene=recipe_generate**：
  - System：家庭菜谱助手角色；只输出一个 JSON 对象，不输出任何其他文字或代码围栏；字段结构说明；用量用家庭可操作表述（"2勺""500g"）；步骤 4~10 步、每步标注时长。
  - User 模板：菜名 `{dishName}`；几人食（默认 2）；调料架约束（SEASONING 列表，调味优先使用这些）；口味画像约束（taste_prefs.summary，遵守忌口）。
- **scene=recipe_iterate**：
  - User 模板：当前菜谱 JSON（指针版本）+ 最近反馈（星级+文字，最多 5 条）+ 要求输出同结构新 JSON，针对反馈调整、未提及部分保持稳定。
  - 输出 schema 为 dual output：`{"recipe": {…菜谱结构}, "taste_summary": {"summary": "…", "tags": […]}}`——一次调用同时产出新版菜谱与口味画像，taste_summary 为空则不动 user_profile。

## 6. 实现分层

```
com.linklife.recipe/
├── controller/   RecipeController(/api/recipes)、PantryController(/api/me/pantry)
├── service/      RecipeService（生成/迭代/编辑/回滚/反馈）、PantryService
├── entity+mapper/ Recipe、RecipeVersion、RecipeFeedback、PantryItem ×4
├── dto/          请求/响应 VO + RecipeContent（结构化菜谱对象，AI 解析目标类型）
└── sse/          RecipeStreamService（SseEmitter 生命周期）
ai/prompt/         提示词模板常量
```

- **SSE 基建**：专用 ThreadPoolTaskExecutor（core 2 / max 4 / CallerRunsPolicy，仿 NotifyAsyncConfig）；`SseEmitter(0L)` + 自管 90s 超时；onCompletion/onTimeout/onError 回调 dispose Flux 订阅防泄漏。
- **事务边界**：SSE 流式方法本身不开长事务；落库在流结束后于独立事务方法中完成。

## 7. 双端页面

| 端 | 页面 |
|---|---|
| 小程序 | ① item 详情"生成菜谱"入口 → 生成页（打字机 + done 后结构化卡片）；② 菜谱详情页（食材/调味/步骤/用量/时长，入口：迭代/反馈/版本/回滚）；③ 我的-调料架管理；④ 反馈提交（星级+文字） |
| Web | 同功能对齐：生成页（fetch 流式）、菜谱详情、版本历史、调料架、反馈 |

小程序 chunked 兼容：基础库 ≥2.20.1；`onChunkReceived` 按 `\n\n` 切帧、增量缓冲处理帧截断。真机流式验证列为 🧑 项。

## 8. 测试策略

- **单元**：JSON 剥围栏/解析/校验、提示词拼装、版本指针回滚逻辑。
- **集成**（Testcontainers + deep-stubs mock ChatClient，抽公共 MockChatClientConfig 复用）：生成落库全链（dish upsert 并发、recipe_id 回填）、迭代版本递增/上限 5/taste_prefs 更新、手动编辑、回滚、反馈、调料架 CRUD/去重、越权（圈外 5001）、SSE 端点 MockMvc asyncDispatch 验证 delta/done/error 事件序、ai_call_log tokens/userId 落库断言。
- **真实验证**：小样本脚本（3~5 道菜：家常菜/带忌口/冷门菜各一）× 检查项（纯 JSON 无围栏率、字段完整率、步骤时长合理率、调料架约束生效、迭代后反馈点被修正、taste_summary 合理）；Key 就绪后执行，结果记录 TODO 随手记录区。
- **部署**：compose 透传 DEEPSEEK_MODEL；deploy/.env 增配 DEEPSEEK_API_KEY/DEEPSEEK_MODEL。

## 9. 执行方式

分支 `feature/p3-recipe-engine`（基于 origin/main）。流程同 P2：writing-plans 写实施计划 → subagent-driven-development 逐任务执行 → 全分支终审 → 更新 PROJECT-STATUS/TODO → 合并。
