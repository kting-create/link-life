# Link-Life 全量待办清单（跨会话记忆）

> **用法**：这是项目全部剩余工作的唯一清单。每次做完一件事就把 `[ ]` 改 `[x]` 并 commit。
> 开新会话时先读 `docs/PROJECT-STATUS.md`，再读本文件，从第一个未勾选项继续。
> 🧑 = 需要你本人线下办理（我无法代办）；其余由 AI 会话执行。

---

## 0. 一次性准备事项（🧑 人工办理，与开发并行）

- [ ] 🧑 注册微信小程序（个人主体）：https://mp.weixin.qq.com → 记下 AppID / AppSecret
- [ ] 🧑 购买云服务器 2c4G（推荐杭州/上海，Ubuntu 22.04 或 24.04）→ 记下公网 IP
- [ ] 🧑 注册域名 + **ICP 备案**（备案要 1-3 周，尽早启动；微信小程序 request 域名必须备案且 HTTPS）
- [ ] 🧑 服务器安装 Docker + Docker Compose 插件
- [ ] 🧑 域名 DNS 解析到服务器 IP；用 certbot 签 Let's Encrypt 证书
- [ ] 🧑 注册 DeepSeek 开放平台账号充值 → 拿到 API Key（P3 才用，可最后办）
- [ ] 🧑 （P2 用）创建飞书群 + 自定义机器人 Webhook 地址
- [ ] 🧑 （可选，P2 用）注册 Bark 或 Server酱，拿推送 Key

---

## 1. P1 点单清单 MVP（下一个开发阶段）

### 1.1 开发前置
- [x] brainstorming 会话细化 P1 范围：清单数据模型、share_token 分享、认领状态机、双端壳范围 → 产出 spec 增量
- [x] writing-plans 写 P1 实施计划到 `docs/superpowers/plans/`
- [x] 本地验证用小程序测试号申请（AppID/Secret 已配 `deploy/.env`；正式小程序注册与域名白名单见第 0 节 🧑 项）

### 1.2 后端（server/）
- [x] 引入 `UserService` 解除 user↔auth 包循环依赖（终审遗留）
- [x] Flyway V2：`order_sheet`、`order_item`、`dish` 表（spec 第 4 节已定字段）
- [x] order 模块：创建点单（选菜/自由输入）→ 生成清单（DRAFT→SHARED 状态机）
- [x] 清单分享：share_token 生成 + 只读分享接口（圈外可读、圈内可操作）
- [x] 认领：claim / release / 标记完成（OPEN→CLAIMED→COOKING→DONE）
- [x] 清单列表/详情/状态流转 API + 集成测试
- [x] `/api/auth/bind` 与邀请码端点加简单限流（如 Caffeine 计数器，每用户每分钟 N 次）

### 1.3 微信小程序端（miniapp/，新建目录）
- [x] 原生小程序工程骨架（登录、请求封装：JWT 存 storage、401 统一刷新重试）
- [x] 圈子页：建圈、邀请码加入、成员列表
- [x] 点单页：选菜/输入菜名生成清单
- [x] 清单页：分享卡片（onShareAppMessage 带 share_token）、认领/标记完成
- [x] 我的页：昵称头像修改、生成绑定码
- [ ] 🧑 微信开发者工具真机预览验证
- [ ] 🧑 上线前将 miniapp/config.js 的 BASE_URL 改为生产 https 域名

### 1.4 Web 端（web/，新建目录，Vue 3 + Vite）
- [x] 工程骨架 + 请求封装（同后端 API，JWT 同规则）
- [x] 登录页（绑定码登录）、圈子/点单/清单页（与小程序功能对齐）
- [x] 分享只读页 `/s/{token}`（未登录可看）
- [x] `npm run build` 产物输出到 `web-dist/`（nginx 已挂载）

### 1.5 部署与收尾
- [x] 更新 `deploy/nginx.conf` 确认 web 静态资源生效；更新 README
- [x] 全量测试 + 更新 PROJECT-STATUS 路线图（P1 ✅）+ 合并推送
- [ ] 🧑 服务器上首次正式部署（按 README 步骤）

---

## 2. P2 推送模块

- [x] Flyway V3：站内通知表 + 通知已读状态
- [x] `NotificationService` 接口 + Spring Event 事件驱动（清单生成/认领/完成触发）
- [x] 通道实现：微信一次性订阅消息（config 门控默认关；🧑 模板开通后的真机验证见随手记录区）
- [x] 通道实现：飞书 Webhook（config 门控默认关）；Bark/Server酱延后
- [x] Web 端兜底：轮询 + 站内信页（小程序端通知页同步完成）
- [x] 通道开关配置化（deploy/.env）+ 测试 + 部署验证（本地 compose 冒烟走降级路径通过）

## 3. P3 AI 菜谱引擎

- [ ] 🧑 确认 DeepSeek API Key 已配置到服务器 .env
- [x] 菜谱数据模型：recipe 表 + 版本化（recipe_version），挂到 dish
- [x] 菜谱生成：AiGatewayService 结构化输出 → 菜谱对象（食材/步骤/用量/时长）
- [x] 口感反馈闭环：用户评价 → AI 迭代菜谱新版本；反馈沉淀 user_profile.taste_prefs
- [x] 自定义能力：新增调味方法/食材、自定义菜谱与个性化命名
- [x] 流式输出接口（生成体验）；ai_call_log 补 tokens 统计与真实 userId
- [x] 提示词调优脚本交付（真实调用小样本清单见 scripts/recipe-sample-validation.md）
- [ ] 🧑 试吃反馈 😄（按 scripts/recipe-sample-validation.md 真实调用验证，Key 就绪后执行；统一验证阶段执行）
- [ ] P3 代码已推送 origin/feature/p3-recipe-engine 并通过分支终审；**按用户决策与 P4/P5 同分支，最后统一验证后一个 PR 合并**

## 4. P4 烹饪引导（feature/p3-recipe-engine 分支）

- [x] 分步计时引擎（类 Keep 趣味计时：步骤倒计时、进度动画、提示音）
- [x] 小程序烹饪模式页（亮屏常亮、步骤切换）
- [x] 过程拍照上传（图片存本地卷 + `/images/` 静态服务，注意 client_max_body_size）
- [x] AI 视觉分析：多模态模型接入（豆包视觉/通义 QVLY，AiGatewayService 扩展 image 接口）
- [x] 分析结果反馈到当前步骤（如"盐放多了"）并写入菜谱迭代数据

## 5. P5 打磨与运维

- [ ] 口味画像数据沉淀与展示；UI 全面打磨
- [ ] Caffeine 缓存落地（热点清单/菜谱）；`wxLogin` 并发竞争 catch-reselect
- [ ] 备份恢复演练（🧑 确认备份真的能恢复）；mysql healthcheck 加 init 等待
- [ ] refresh-token 吊销方案；`uk_unionid`/`uk_code` 索引修正迁移
- [ ] 性能压测（小规模即可）+ 安全检查清单过一遍

---

## 6. 随手记录区（会话中想到的都丢这里，定期归类）

- 🧑 开通微信订阅消息模板后需单独小迭代（订阅授权埋点 + wx-subscribe 模板字段映射真机验证）
- 🧑 飞书群机器人 Webhook 待配置（第 0 节待办保留），配置后需真跑一遍"建单 → 认领 → 收单"确认飞书群收到 3 条摘要
- Bark/Server酱通道延后
- P2 双端通知 UI 手工验证延后（单机环境无第二设备；API 级已验证，步骤见 PROJECT-STATUS 第 6 节）
- 本地 compose DB 有 P2 验证残留数据（verify-user-A/B、29 张单、27 条通知），不需要可 `docker compose down -v` 清空
- 🧑 微信开发者工具真机流式验证待做（SSE enableChunked，需基础库 ≥2.20.1；P3 生成/迭代流式输出真机预览）
- 小样本真实验证清单见 `scripts/recipe-sample-validation.md`（DeepSeek Key 就绪后执行，结果追加到本区）
- 🧑 阿里云百炼 API Key（DASHSCOPE_API_KEY）待配置到 deploy/.env —— P4 视觉分析真跑与统一验证依赖（缺省时 compose 注入占位 key，视觉分析返回 6005 降级，不影响启动）
- P4 双端 UI 手工验证延后（与 P3 真机流式验证合并到统一验证阶段）
