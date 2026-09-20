# 压测基线记录(P5)

## 环境说明

- 本机 macOS,Docker Desktop,compose 栈(app + mysql:8.0 + nginx:1.27-alpine),全栈 healthy 后执行。
- app 为单实例,MySQL `innodb_buffer_pool_size=128M`、`max_connections=50`(见 `deploy/docker-compose.yml`)。
- 本机配置:15 核 / 48GB,但受 Docker Desktop 资源限制与单实例约束,**仅供相对对比基线**,不代表生产容量。
- 工具:ab(ApacheBench 2.3,macOS 自带),经 nginx(:80)打点。
- 测试对象均为只读热点接口;`SHARE_TOKEN` 取自本机库 `order_sheet.share_token` 历史数据。
- 登录态接口(`/api/circles` 等)本轮未跑(AUTH_TOKEN 需走微信登录流程,较重,跳过;脚本已支持可选参数)。

## 执行命令

```bash
SHARE_TOKEN=SXi2mM4dXk9k ./scripts/load-test.sh          # n=1000 c=10 三组
# c=50 轮为手工执行:ab -n 1000 -c 50 <url>
```

## 结果(n=1000,失败请求均为 0)

### c=10

| 接口 | 路径 | RPS | p50 (ms) | p99 (ms) |
|---|---|---|---|---|
| health | `/api/health` | 4444 | 2 | 5 |
| share_view | `/api/share/{token}`(查库) | 5324 | 2 | 4 |
| share_page | `/s/{token}`(SPA index.html) | 2630 | 2 | 8 |

### c=50

| 接口 | 路径 | RPS | p50 (ms) | p99 (ms) |
|---|---|---|---|---|
| health | `/api/health` | 9723 | 4 | 11 |
| share_view | `/api/share/{token}`(查库) | 9656 | 5 | 9 |
| share_page | `/s/{token}`(SPA index.html) | 1312 | 25 | 93 |

### 原始 ab 关键行(节选)

```
== health n=1000 c=10 ==
Requests per second:    4444.03 [#/sec] (mean)
Time per request:       2.250 [ms] (mean)
  50%      2
  99%      5
== share_view n=1000 c=10 ==
Requests per second:    5324.27 [#/sec] (mean)
Time per request:       1.878 [ms] (mean)
  50%      2
  99%      4
== share_page n=1000 c=10 ==
Requests per second:    2630.03 [#/sec] (mean)
Time per request:       3.802 [ms] (mean)
  50%      2
  99%      8
```

share_view(c=10)完整分位:95% 3ms / 98% 3ms / 99% 3ms / 100% 4ms(最长请求)。

## 结论

- **数据库读接口不是瓶颈**:`/api/share/{token}` 全链路(nginx → Spring → MySQL 查询)在 c=50 下仍达 9656 RPS、p99 9ms,**未见明显慢点(无 N+1 迹象)**,不做代码改动。
- `share_page`(/s/ 路由回退到 SPA index.html)在 c=50 下明显慢于 API(1312 RPS、p99 93ms):每次回退都返回完整 index.html,无缓存头、无压缩,单请求字节数大。属 nginx 静态层现象,页面实际加载后静态资源带 `expires 30d`(/images/)/默认 HTML 无缓存,可接受;后续可在 nginx 对 index.html 加 gzip/短缓存,本轮不改动。
- health 与 share_view 吞吐同量级,说明 JSON 序列化与 DB 查询开销在本规模下不构成差异。
