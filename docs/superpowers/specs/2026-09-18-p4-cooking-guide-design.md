# P4 烹饪引导 设计文档

日期:2026-09-18
状态:已与需求方确认
分支:feature/p3-recipe-engine(与 P3/P5 同分支,最后统一验证一次性合并)

## 1. 范围与目标

在 P3 菜谱引擎基础上新增烹饪过程引导:

1. **分步计时引擎**(类 Keep):步骤倒计时、进度动画、提示音,双端(小程序 + Web)均实现
2. **小程序烹饪模式页**:亮屏常亮(`wx.setKeepScreenOn`)、步骤切换、步骤内拍照
3. **过程拍照上传**:multipart 上传,图片存本地卷,nginx `/images/` 静态服务(已就位)
4. **AI 视觉分析**:阿里百炼 Qwen3-VL(OpenAI 兼容端点),`AiGatewayService` 扩展 image 接口;分析结果反馈到当前步骤,用户确认后 AI 只输出**部分修改补丁**(changes)并合并生成菜谱新版本(source=PHOTO_ANALYSIS)

不在范围内:自动应用补丁(用户确认后才应用)、照片对象存储备份(P5 运维覆盖)、Web 端亮屏常亮(浏览器能力限制,Web 烹饪模式仅步骤+计时)。

## 2. 关键决策记录

- **多模态供应商**:阿里百炼 Qwen3-VL 系列(`qwen3-vl-plus`/`qwen3-vl-flash`,默认 flash,模型名可配)。DeepSeek 无 vision API,不复用现有 deepseek 链路。
- **接入方式**:引入 `spring-ai-starter-model-openai`(BOM 已管理 1.0.0),按 Spring AI 官方 "Multiple OpenAI-Compatible API Endpoints" 模式接入百炼 `https://dashscope.aliyuncs.com/compatible-mode/v1`。**不引入** spring-ai-alibaba starter(仅 1.0 线兼容且已停止主线演进,拖 Boot 3.4.5 传递依赖);不手写 HTTP 客户端(丢抽象、tokens 统计全手写)。
- **双 ChatModel 并存**:容器中出现两个 ChatModel 后,Spring AI 不再自动装配单一 `ChatClient.Builder`。官方方案:配 `spring.ai.chat.client.enabled=false`,手动为 deepseek/openai 两个 ChatModel 各建 `ChatClient` bean,注入处用 `@Qualifier`。`AiGatewayService` 现有基于 `ChatClient.Builder` 的构造需同步改造(文本链路行为不变,85 个既有测试仍须全绿)。
- **图片传输**:前端上传原图字节到后端,后端落盘本地卷;AI 分析时后端读文件转 base64 data URL 传给百炼(官方支持 `data:image/{mime};base64,...`,MIME 须与实际格式一致)。
- **补丁确认制**:烹饪场景手湿交互要少,但误判直接改菜谱不可接受 → 分析结果先展示,用户点"应用"才生成新版本;误改可用现有版本回滚兜底。
- 🧑 依赖事项:需注册阿里云百炼并创建 API Key(`DASHSCOPE_API_KEY`,地域须与端点匹配);缺失时分析接口降级(见 §5)。

## 3. 数据模型(Flyway V5)

```sql
CREATE TABLE recipe_photo (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  recipe_id     BIGINT NOT NULL,
  step_no       INT    NOT NULL,
  uploader_id   BIGINT NOT NULL,
  file_path     VARCHAR(255) NOT NULL,      -- 相对路径 images/recipes/{recipeId}/{uuid}.{ext}
  size_bytes    BIGINT NOT NULL,
  analysis      JSON NULL,                  -- {advice, changes:[{stepNo, text?, durationSec?}]}
  analyzed_at   DATETIME NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_photo_recipe (recipe_id, step_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- `recipe_version.source` 值域扩展:`AI_GENERATE / AI_ITERATE / MANUAL_EDIT` + **`PHOTO_ANALYSIS`**(change_note = AI 建议摘要,截 255,沿用现有 5 版上限与指针回滚,无新迁移,仅语义扩展)。

## 4. 后端 API(recipe 模块内新增)

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/recipes/{id}/steps/{stepNo}/photos` | multipart 上传(JWT)。校验:圈内成员、扩展名+魔数 jpg/jpeg/png/webp、单文件 ≤5MB、每菜谱 ≤50 张。返回 `{id, url, stepNo, createdAt}`(url 为相对路径 `/images/...`) |
| GET | `/api/recipes/{id}/photos` | 按步骤分组返回照片列表(圈内成员) |
| DELETE | `/api/photos/{photoId}` | 上传者或圈主可删;同时删文件 |
| POST | `/api/photos/{photoId}/analysis` | 触发 AI 视觉分析(同步)。上下文=菜名+当前版本该步骤文本+user_profile.taste_prefs;Qwen3-VL 输出 JSON `{advice, changes[]}` 存 photo.analysis 并返回 |
| POST | `/api/photos/{photoId}/apply` | 用户确认应用:将 changes 合并进当前版本 content(改 text/durationSec)→ 写新版本(source=PHOTO_ANALYSIS, change_note=advice 截 255);changes 为空时 6006 |

### AiGatewayService 扩展

- 新增 `callStructuredWithImage(userId, scene, prompt, imageBytes, mimeType, Class<T>)`:走 qwen ChatClient,`UserMessage` + `Media`(ByteArrayResource);结构化 JSON 解析失败 → RECIPE_PARSE_FAILED 同款路径(视觉场景用 6005)。
- 日志复用 `ai_call_log`:provider=`qwen`、model=配置值、scene=`photo_analysis`(V1 注释已预留)、tokens 照记。
- 提示词模板收口到与 P3 相同的模板机制。

### 配置与部署

```yaml
spring.ai:
  chat.client.enabled: false          # 双 ChatModel 并存,关闭自动 Builder(官方方案)
  openai:
    api-key: ${DASHSCOPE_API_KEY:}
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    chat.options.model: ${DASHSCOPE_VL_MODEL:qwen3-vl-flash}
server.servlet... multipart: max-file-size 6MB / max-request-size 8MB
```

- compose:app 容器新增卷挂载 `../data/images:/data/images:rw`(nginx 现有 `:ro` 不动);`deploy/.env` 增加 `DASHSCOPE_API_KEY`(可空,降级运行)、`DASHSCOPE_VL_MODEL`。
- nginx:`client_max_body_size 10m` 已 ≥ 8MB 上限,不改;`/images/` alias 已就位。
- **降级**:`DASHSCOPE_API_KEY` 缺失或调用失败 → analysis 返回 6005,写 ai_call_log(与 P3 占位符模式一致)。

### 错误码(新开 6xxx 段,common/exception/ErrorCode.java)

| 码 | 名称 | 场景 |
|---|---|---|
| 6001 | FILE_TYPE_INVALID | 扩展名/魔数非法 |
| 6002 | FILE_TOO_LARGE | >5MB(后端层校验) |
| 6003 | PHOTO_LIMIT_EXCEEDED | 每菜谱 >50 张 |
| 6004 | PHOTO_NOT_FOUND | 照片不存在 |
| 6005 | VISION_AI_FAILED | Key 缺失/调用失败/JSON 解析失败 |
| 6006 | NOTHING_TO_APPLY | changes 为空时 apply |
| 6007 | PHOTO_NO_PERMISSION | 非上传者且非圈主删除照片 |
| 6008 | PHOTO_STEP_INVALID | 上传时 stepNo 不在当前版本步骤范围 |

## 5. 前端

### 小程序(烹饪模式主场)

- 新页 `pages/cook-mode/cook-mode`:菜谱详情页"开始烹饪"按钮进入(带 recipeId)。
  - `onShow` `wx.setKeepScreenOn(true)`,`onHide/onUnload` 恢复 false
  - 步骤大字展示 + 顶部整体进度(x/n);`durationSec>0` 的步骤进入即自动倒计时,进度环动画(setInterval 驱动 CSS transform),可暂停/继续/跳过
  - 倒计时归零:`wx.createInnerAudioContext` 播放内置 CC0 提示音(ding.mp3,打包进 miniapp/assets)+ `wx.vibrateLong`;无音频文件时振动兜底
  - 步骤切换:上一/下一步按钮 + 手势滑动
  - 每步底部"拍照"按钮:`wx.chooseMedia`(camera,1 张)→ 新封装 `utils/upload.js`(`wx.uploadFile` + JWT header,处理 401 刷新重试与 Result 错误码)→ 上传成功即在步骤下展示缩略图;"问 AI"按钮 → 调 analysis → 建议卡片展示 advice + changes 预览 → "应用"(调 apply,提示已生成新版本)/"忽略"
- `recipe-detail` 修 P3 遗留:手动编辑 steps 时保留 `durationSec`(与 Web 端行为对齐)。

### Web(Vue 3)

- 新路由 `/recipes/:id/cook` 烹饪模式页:步骤切换 + 倒计时 + Web Audio API 合成提示音(无需音频素材);从 RecipeDetail 进入。
- RecipeDetail:照片按步骤展示(缩略图);手机浏览器经 `<input type="file" accept="image/*" capture="environment">` 上传;分析/应用与小程序共用同一 API。

## 6. 测试策略

- 集成测试(沿 `IntegrationTestBase` + MockMvc):
  - 上传:成功落盘/类型非法 6001/超限 6002(构造 >5MB mock)/数量超限 6003/圈外用户 401 或 3006 语义沿用/删除权限(非上传者非圈主拒绝)
  - 分析:`@MockBean` qwen ChatClient 返回合法 JSON(advice+changes)→ 存库并返回;返回空 changes;返回非法 JSON → 6005;Key 缺失路径 → 6005 且写 ai_call_log
  - apply:changes 合并生成新版本(source=PHOTO_ANALYSIS、change_note 截断、版本 +1、指针前移);空 changes 6006;5 版上限沿用 5002;越权拒绝
  - 既有 85 测试全绿(`spring.ai.chat.client.enabled=false` 改造后回归验证)
- 双端 UI 手工验证延后至统一验证阶段(与 P3 真机验证合并)。

## 7. 风险与开放问题

- 双 starter 并存自动装配冲突:已按官方文档方案处理,风险低,但需在实现早期先做一条冒烟(文本生成链路回归)。
- 百炼地域与 API Key 匹配(北京/新加坡域名不同):base-url 可配,默认北京。
- 2c4G 磁盘图片增长:5MB×50×菜谱数上限可控;清理/备份策略归 P5 运维。
- Web Audio 自动播放策略:烹饪模式页由用户手势进入后触发,常规场景不受限;真机验证阶段确认。
