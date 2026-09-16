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
DEEPSEEK_API_KEY=后续AI功能用
```

3. 启动：

```bash
cd deploy && docker compose up -d
curl http://localhost/api/health
```

4. 每日备份：`crontab -e` 添加 `0 3 * * * /opt/link-life/deploy/backup.sh`

## 进度

见 `docs/superpowers/specs/2026-09-16-link-life-design.md` 第 9 节路线图。
