# 安全检查清单(P5)

逐项列:现状(引代码事实)/ 风险 / 处置。标注 [P5] 表示本轮已落地的项;🧑 表示需人工操作的事项。

## 1. 鉴权(JWT / refresh / 吊销 / 绑定码)

**现状**
- access token TTL 168h(7 天),refresh TTL 30 天:`server/src/main/resources/application.yml:35-36`(`link.jwt.access-ttl-hours: 168`、`refresh-ttl-days: 30`)。
- refresh 续签校验类型(`"refresh".equals(info.type())`)与 `token_version` 不匹配即 `TOKEN_REVOKED`:`AuthService.refresh`(AuthService.java:53-70)。
- 吊销:登出/关键变更 `bumpTokenVersion` 原子自增 `token_version`:`UserService.java:39-42`;校验点覆盖 refresh 与鉴权过滤器。[P5 已落地]
- 绑定码:6 位 SecureRandom、TTL 10 分钟;核销用 `markUsed(id)` 条件 UPDATE 原子占位,占位失败即拒绝,防并发复用:`BindingCodeService.bind`(BindingCodeService.java:45-63)。

**风险**
- access token 7 天有效期偏长,期间无法主动吊销单 token(仅能靠 bump version 全量吊销)。
- 绑定码 6 位数字,10 分钟窗口内存在爆破面(依赖限流兜底)。

**处置**
- [P5] token_version 吊销机制已落地并测试(TokenVersionTest)。
- 后续可评估缩短 access TTL 至 2h 并由小程序端静默续签(不在本轮)。

## 2. 越权(圈成员校验 / 分享页只读)

**现状**
- `CircleService.requireMembership` 为唯一成员校验入口,覆盖:
  - OrderService:5 处(创建/查询/更新/删除/结算,OrderService.java:67,90,111,134,150)
  - RecipeService:2 处(RecipeService.java:72,108)、RecipeGenerationService:1 处
  - CircleService 自身(join/members 等)
- 分享接口仅 `@GetMapping("/api/share/{token}")` 一个只读端点(ShareController.java:16),无写方法;分享页 `/s/:token`(web/src/router.js:24)只读渲染。

**风险**
- 新增写端点若遗漏 `requireMembership` 会引入越权(当前覆盖面核对无遗漏)。

**处置**
- [P5] 覆盖面核对完成,全部圈数据写路径经 requireMembership;Code Review 时以此为检查项。

## 3. 上传(扩展名 / 魔数 / 大小 / 数量 / 路径遍历)

**现状**(`ImageStorageService.java`)
- 大小上限 5MB(`MAX_FILE_BYTES = 5 * 1024 * 1024`,:19)且字节数校验(:77)。
- 扩展名白名单 + 魔数校验(`magicOk(bytes, ext)`,:84,:94),防伪造扩展名。
- 路径遍历防护:目标路径 `normalize()` 后必须仍在 `baseDir` 内(:65,:70-71 `resolveSafely`)。
- 图片数量上限由 PhotoService 上传入口控制(每 recipe 有限制)。

**风险**
- nginx `/images/` 为公开静态服务,知道 URL 即可访问(无签名 URL);图片含生活场景,敏感度低但需知晓。

**处置**
- [P5] 校验链(扩展名+魔数+大小+路径遍历)已齐;nginx `client_max_body_size 10m` 兜底(deploy/nginx.conf:5)。

## 4. 限流

**现状**(统一 `RateLimiter`,`com.linklife.common.ratelimit`)
- `bind`:按 IP(`RateLimiter.clientIp(httpRequest) + ":bind"`,AuthController.java:51)
- `refresh`:按 IP(AuthController.java:38)
- `wx-login`:按 IP(AuthController.java:31)
- `join`:按用户(`UserContext.requireUserId() + ":join"`,CircleController.java:35)
- AI 端点(generate/iterate):按用户(RecipeController.java:89,100)[P5 已扩展]

**风险**
- 限流为单机内存实现,多实例部署时不共享计数(当前单实例,可接受)。

**处置**
- [P5] 端点覆盖核对完成(bind/join/wx-login/refresh/AI 全覆盖)。

## 5. 密钥管理

**现状**
- JWT_SECRET fail-fast:生产环境(非 dev profile)使用默认密钥即拒绝启动(`JwtService.java:25`;测试 `JwtSecretProdFailFastTest`)。[P5 已落地]
- compose 强制注入:`JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}`(deploy/docker-compose.yml:10)。
- **`deploy/.env` 未提交 git**:`.gitignore:9` 已排除 `deploy/.env`;经全量 git 历史核查(遍历所有 commit 的 tree),历史上从未存在任何 `.env` 文件,密钥(WX_SECRET/DEEPSEEK_API_KEY/DASHSCOPE_API_KEY)未进版本库。计划文档中"已提交 git"的前提与实际不符,此处如实修正。

**风险**
- 本地 `deploy/.env` 含真实测试号密钥,若曾通过聊天/网盘等方式外发文件则等同泄露。

**处置**
- 🧑 保守起见轮换密钥:小程序测试号在微信后台重置 WX_SECRET;DeepSeek / DashScope 控制台吊销旧 key 换新,只更新本地 `deploy/.env`。
- git 历史清理:**无需**(已验证历史无 .env blob);保持 `.gitignore` 现状即可。

## 6. 传输

**现状**
- 本机部署为 HTTP(nginx :80);nginx 反代仅信任 `X-Real-IP/X-Forwarded-For`。
- `client_max_body_size 10m`(deploy/nginx.conf:5)。

**风险**
- 上线公网若仍为 HTTP,token/绑定码明文传输可被截获。

**处置**
- 🧑 上线时配置全站 HTTPS(证书 + 80 强制跳 443),并在网关层将 `X-Forwarded-For` 置为可信代理链。

## 7. 依赖与部署面

**现状**
- 镜像版本固定:`mysql:8.0`、`nginx:1.27-alpine`(deploy/docker-compose.yml)。
- 最小暴露面:mysql/app 均 `expose`(仅容器网络内可见),仅 nginx 映射 `ports: "80:80"`(:50)。
- MySQL `max_connections=50`、buffer pool 128M,有上限防打满。

**风险**
- 镜像 tag 非摘要固定(minor tag 仍会漂移 patch 版本)。

**处置**
- 已用非 latest 固定 tag;后续可按需 pin 到 digest(可选项,非必须)。

## 8. 已知残余风险(如实列出)

1. **refresh-token 30 天窗口**:refresh token 泄露后 30 天内可换新 access;仅当用户登出(bump token_version)才失效。缓解:refresh 端点已限流;可评估加 rotation(每次 refresh 作废旧 refresh)。
2. **backup.sh 密码经 env**:`deploy/backup.sh` 通过环境变量传 MySQL 口令,进程列表/审计日志可能可见(本机单人环境,风险低);已避免命令行明文参数。
3. **绑定码爆破面**:6 位数字 + 10 分钟 TTL,依赖 bind 端点 IP 限流兜底。
4. **限流单机内存态**:水平扩容后需换集中式(Redis)实现。
5. **公网传输明文**:HTTPS 待上线部署时落地(见第 6 节)。
