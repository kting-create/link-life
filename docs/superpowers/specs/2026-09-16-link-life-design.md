# Link-Life 设计文档

日期：2026-09-16
状态：已与需求方确认定稿

## 1. 项目概述

Link-Life 是一个面向家庭、朋友间的轻协作平台。核心场景：用户发起"点单"（非真实交易），生成一份菜品清单，分享给好友认领；后续引入 AI 能力，为每道菜提供详细菜谱、口感反馈迭代、烹饪过程引导（分步计时、拍照 AI 分析）。

- 载体：微信小程序 + Web 网页端
- 双端关系："厚后端 + 薄双端"——AI 调用、Skill、MCP、推送、内容生成等能力统一收口在后端，小程序与 Web 只是两个前端壳，调用同一套 HTTPS API
- 规模：小范围使用（家庭/朋友圈），单台 2c4G 云服务器
- 部署方式：逐步迭代，功能分阶段添加

## 2. 总体架构（方案 A：模块化单体 + 原生双端）

```
┌─────────────┐   ┌──────────────┐
│ 微信小程序   │   │ Web (Vue 3)  │   ← 薄双端，各自独立
└──────┬──────┘   └──────┬───────┘
       │   同一套 HTTPS API（JWT 统一鉴权）
       ▼                 ▼
┌─────────────────────────────────────┐
│   Spring Boot 3 模块化单体           │
│  ┌────────┬─────────┬────────────┐  │
│  │用户/圈子│ 清单模块 │ AI 网关层   │  │
│  │        │         │ (Spring AI) │  │
│  └────────┴─────────┴────────────┘  │
│  推送模块（NotificationService 多通道）│
└──────┬──────────┬───────────────────┘
       ▼          ▼
     MySQL    本地文件(图片)      ← 2c4G 单机，Docker Compose
```

### 技术选型

| 层 | 选型 | 理由 |
|---|---|---|
| 后端 | Spring Boot 3 + MyBatis-Plus | 需求方熟悉 Java；模块化单体，按模块分包，模块间只走接口调用 |
| AI 框架 | Spring AI（备选 LangChain4j） | 多供应商抽象、Tool Calling、结构化输出、MCP Client/Server、流式输出原生支持；与 Spring Boot 无缝集成 |
| 小程序 | 微信原生 | 个人主体限制下体验最原生 |
| Web | Vue 3 + Vite | 独立前端壳 |
| 数据库 | MySQL 8 | 单实例，数据卷挂载 |
| 缓存 | Caffeine 进程内缓存 | 小规模不上 Redis，省内存 |
| 部署 | Docker Compose 单机 | Nginx + app + MySQL |
| HTTPS | Let's Encrypt + certbot 自动续期 | 免费证书 |

### 架构决策记录

- **不采用微服务**：2c4G 内存无法承载多个 JVM，模块化单体是唯一合理选择
- **不采用跨端框架（Taro/uni-app）**：需求方明确"底层调用一致"指后端能力收口，非前端技术统一
- **不采用 Redis**：Caffeine 进程内缓存满足小规模场景，节省约 512M 内存
- **AI 走云端 LLM API**：2c4G 无法本地部署有能力的模型；通过 Spring AI 抽象层实现供应商可切换（DeepSeek/豆包/通义等）

## 3. 用户 / 圈子模块

### 数据模型

| 表 | 关键字段 | 说明 |
|---|---|---|
| `user` | id, openid, unionid, nickname, avatar, phone | 微信登录自动创建；Web 端绑定到同一行 |
| `circle` | id, name, owner_id, invite_code | 家庭/朋友圈子，邀请码加入 |
| `circle_member` | circle_id, user_id, role, join_time | role: OWNER / MEMBER |
| `user_profile` | user_id, taste_prefs (JSON), ai_memory | 口味偏好、忌口等，AI 反馈数据沉淀于此 |

### 登录与鉴权（个人主体约束下的设计）

- 小程序端：`wx.login` → 后端 code2session → 签发 JWT（7 天 + refresh token）
- Web 端登录（个人主体无法使用微信网站应用扫码登录）：
  1. 手机验证码登录；或
  2. 小程序"我的"页生成 6 位绑定码，Web 端输入绑定码关联同一账号（推荐，零资质依赖）
- 统一鉴权：所有 API 走 `Authorization: Bearer <JWT>`，双端一致

## 4. 点单清单模块（MVP 核心）

### 数据模型

| 表 | 关键字段 | 说明 |
|---|---|---|
| `order_sheet` | id, circle_id, creator_id, title, status, share_token | status: DRAFT / SHARED / IN_PROGRESS / COMPLETED |
| `order_item` | id, sheet_id, dish_name, note, claimant_id, item_status | item_status: OPEN / CLAIMED / COOKING / DONE |
| `dish` | id, circle_id 或 user_id, name, recipe_id | 菜品档案，MVP 仅有名称，后续挂 AI 菜谱 |

### 核心流程

```
建圈子 → 发起点单（选菜或自由输入）→ 分享 → 好友认领 → 标记完成
```

### 分享机制（个人主体约束下的设计）

- 小程序内：原生分享卡片（`onShareAppMessage`）携带 `share_token`，好友在微信内点卡片直达清单详情
- Web 端：普通 https 链接 `https://yourdomain.com/s/{share_token}`，仅浏览器打开；**不做跳转小程序的 URL Link/Short Link（个人主体不可用）**
- 权限：清单归属圈子，圈内成员可操作；圈外通过分享链接仅只读

## 5. AI 网关层（P3/P4 的地基，MVP 只搭骨架）

- 基于 Spring AI 的 `ChatClient` 抽象，业务代码不感知具体供应商
- MVP 阶段：引入依赖 + 配置一个供应商（DeepSeek 或豆包）+ 调用日志表，不做具体 AI 功能
- 后续能力全部挂在这一层之下：
  - P3 菜谱引擎：菜谱生成（结构化输出映射菜谱对象）、口感反馈迭代（菜谱版本化存储）、自定义调味/食材/命名
  - P4 烹饪引导：分步趣味计时（类 Keep）、过程拍照上传、AI 视觉分析（调料多少等，走多模态接口）
  - MCP 工具与 Skill 编排：注册在 Spring AI 的 MCP 框架之下

## 6. 推送模块（P2 实现，架构预留）

- 统一 `NotificationService` 接口，多通道实现：
  - 微信订阅消息（个人主体可用一次性订阅）
  - 飞书 Webhook
  - Bark / Server酱（手机推送，零门槛）
  - Web 端兜底：页面轮询 + 站内信
- 触发点：清单生成、有人认领、菜品完成等事件
- 实现方式：Spring Event 事件驱动解耦，通道可配置开关

## 7. 部署与运维

```
Docker Compose:
  nginx   → 静态资源(Web) + 反代 API + 图片静态服务
  app     → Spring Boot jar（-Xmx768m）
  mysql   → 8.x，数据卷挂载
```

- JVM 堆上限 768m，MySQL 调低 buffer pool，总内存占用控制在 2G 内
- 图片存本地磁盘挂载卷，Nginx 直接静态服务
- 备份：每日 mysqldump + 图片目录增量备份到对象存储（OSS/COS）
- 日志：logback 滚动文件（不上 ELK）

## 8. 测试策略

- 后端：JUnit 5 + Mockito 单元测试；模块边界接口用集成测试（Testcontainers 起 MySQL）
- 核心 API（鉴权、清单认领状态流转）必须有集成测试覆盖
- 前端：以手工验收为主，关键组件补充组件测试
- AI 相关：mock LLM 响应测试业务逻辑；真实调用以小样本验证提示词效果

## 9. 路线图与进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 基础框架 | 工程骨架、用户/圈子、JWT 鉴权、Docker Compose 部署 | ✅ 完成 |
| P1 点单清单 MVP | 点单/清单/认领/分享、小程序端 + Web 端 | ⬜ |
| P2 推送 | 订阅消息 + 飞书/Bark webhook、事件驱动 | ⬜ |
| P3 AI 菜谱引擎 | 菜谱生成、口感反馈迭代、自定义调味/食材/命名、菜谱版本化 | ⬜ |
| P4 烹饪引导 | 分步趣味计时、过程拍照、AI 视觉分析反馈 | ⬜ |
| P5 打磨 | 数据沉淀（口味画像）、UI 打磨、性能优化 | ⬜ |

每完成一个阶段，更新本表状态。
