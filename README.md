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

## 进度

见 `docs/superpowers/specs/2026-09-16-link-life-design.md` 第 9 节路线图与 `docs/PROJECT-STATUS.md`。
