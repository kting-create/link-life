# Link-Life

家庭/朋友间的点单清单 + AI 菜谱平台。设计文档见 `docs/superpowers/specs/`。

## 本地开发（后端）

```bash
cd server
mvn spring-boot:run   # 需本地 MySQL，或用 docker 起一个
```

## 部署（2c4G 云服务器）

1. 安装 Docker 与 Docker Compose 插件
2. 在 `deploy/` 下创建 `.env`：

```
DB_PASSWORD=你的数据库密码
JWT_SECRET=至少32字节的随机字符串
WX_APPID=小程序appid
WX_SECRET=小程序secret
DEEPSEEK_API_KEY=后续AI功能用（可选；不配置时启动会注入占位 key，AI 调用会失败并记录 ai_call_log，不影响其他功能，后续阶段需要真实 key 才能调用 AI）

# P2 推送通道（可选，默认关闭，不配置不影响启动）
# FEISHU_NOTIFY_ENABLED=true
# FEISHU_WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/xxx
# WX_SUBSCRIBE_ENABLED=true
# WX_SUBSCRIBE_TEMPLATE_ID=订阅消息模板ID
```

3. 启动：

```bash
cd deploy && docker compose up -d
curl http://localhost/api/health
```

4. 每日备份：`crontab -e` 添加 `0 3 * * * /opt/link-life/deploy/backup.sh`

## Web 端（P1）

Vue 3 + Vite 单页应用，构建产物输出到 `web-dist/`（nginx 容器已挂载该目录作为静态根，`/s/{token}` 分享页走 SPA fallback）：

```bash
cd web && npm install && npm run build
```

本地开发：`cd web && npm run dev`（开发服务器代理 `/api` 到本地后端）。

## P1 功能（点单清单 MVP）

- **点单清单**：发起点单（选菜/自由输入）→ 生成清单（DRAFT → SHARED 状态机），清单支持列表、详情与状态流转
- **认领状态机**：清单内菜品认领（OPEN → CLAIMED → COOKING → DONE），支持 claim / release / 标记完成
- **分享**：清单生成 share_token，微信小程序分享卡片 + Web 只读分享页 `/s/{token}`（免登录可看，错误码 3001 表示清单不存在）
- **双端壳**：微信小程序（登录、圈子、点单、清单、我的）与 Web 端（绑定码登录 + 功能对齐页面）

## P2 功能（推送模块）

事件驱动的多通道通知（站内信 + 外部通道），两外部通道默认关闭、缺配置静默降级不影响启动：

- **站内通知**：清单生成/认领/收单等事件异步写入站内信，Web 端通知页 + 未读角标（30s 轮询），小程序端通知页 + 角标
- **飞书群机器人**：事件摘要推送到飞书群 Webhook（`FEISHU_NOTIFY_ENABLED=true` + Webhook 地址时启用）
- **微信订阅消息**：一次性订阅消息推送（`WX_SUBSCRIBE_ENABLED=true` + 模板 ID 时启用；需小程序后台开通模板）

### 推送通道环境变量

| 变量名 | 作用 | 默认值 | 缺省行为 |
|---|---|---|---|
| `FEISHU_NOTIFY_ENABLED` | 飞书通道总开关 | `false` | 通道禁用，不发送 |
| `FEISHU_WEBHOOK_URL` | 飞书群机器人 Webhook 地址 | 空 | 通道降级跳过，不报错 |
| `WX_SUBSCRIBE_ENABLED` | 微信订阅消息总开关 | `false` | 通道禁用，不发送 |
| `WX_SUBSCRIBE_TEMPLATE_ID` | 订阅消息模板 ID | 空 | 通道降级跳过，不报错 |

### P4 烹饪引导环境变量

| 变量名 | 作用 | 默认值 | 缺省行为 |
|---|---|---|---|
| `DASHSCOPE_API_KEY` | 阿里云百炼 API Key（P4 视觉分析） | 占位 key（compose 注入 `sk-placeholder-configure-real-key`） | 真配占位/错误 key 时调用失败，视觉分析走 6005 降级；**不可显式置空**（空字符串会使 Spring AI OpenAI 自动配置启动断言失败，故 compose 默认注入占位 key） |
| `DASHSCOPE_VL_MODEL` | 视觉模型名 | `qwen3-vl-flash` | — |
| `IMAGE_BASE_DIR` | 图片存储根目录（图片落盘 `<根>/images/recipes/...`，compose 挂载 `../data/images:/data/images`） | `/data` | 走默认 `/data`，与 nginx `alias /data/images/` 对应 |

## 进度

见 `docs/superpowers/specs/2026-09-16-link-life-design.md` 第 9 节路线图与 `docs/PROJECT-STATUS.md`。
