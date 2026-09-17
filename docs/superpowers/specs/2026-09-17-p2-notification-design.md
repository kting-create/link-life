# P2 推送模块设计（spec 增量）

日期：2026-09-17
状态：已与需求方确认定稿
父文档：`docs/superpowers/specs/2026-09-16-link-life-design.md`（第 6 节为推送模块的设计基线，本文档为其 P2 实施增量）

## 0. 范围与决策记录

- **交付顺序**：后端全部完成（含集成测试）→ Web 端 → 小程序端 → 部署收尾
- **通道范围**：站内信（必发）+ 飞书 Webhook + 微信一次性订阅消息；**Bark/Server酱 延后**，等有真实需求再做（届时需 user 表加 push key 字段）
- **飞书定位**：全局一个群 Webhook（`FEISHU_WEBHOOK_URL`），所有事件摘要发同一个群，不做圈级配置
- **事件驱动方案（方案 A）**：领域服务 publish Spring Event，`@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 异步分发；事务回滚不发假通知，通道失败不影响主流程
- **小程序端范围**：只做站内通知列表页；`wx.requestSubscribeMessage` 授权埋点延后——测试号环境不支持订阅消息模板，等 🧑 开通模板后单独小迭代并真机验证
- **降级原则**：所有通道开关默认关闭、缺配置可启动不报错（仿 P0 对 `DEEPSEEK_API_KEY` 的占位符处理）；通道内任何异常只记日志不上抛
- **触发点矩阵（4 个事件）**：
  1. 清单生成（SHARED）→ 圈内除发起人外所有成员
  2. 认领 → 点单创建者
  3. 菜品标记 DONE → 点单创建者
  4. 整单 COMPLETED → 圈内除发起人外所有成员
  加菜、释放认领不通知

## 1. 数据模型（Flyway V3）

```sql
CREATE TABLE notification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '接收人',
    type VARCHAR(32) NOT NULL COMMENT 'SHEET_SHARED / ITEM_CLAIMED / ITEM_DONE / SHEET_COMPLETED',
    title VARCHAR(255) NOT NULL,
    content VARCHAR(255) NOT NULL,
    sheet_id BIGINT NULL COMMENT '跳转目标（清单）',
    circle_id BIGINT NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_read (user_id, is_read),
    KEY idx_user_id (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- 不修改 `user` 表（Bark 延后，无需 push key 字段）
- 通知文案落库时渲染完成，读取端不做二次格式化

### 文案模板

| type | title | content |
|---|---|---|
| SHEET_SHARED | {发起人} 发起点单「{标题}」 | 共 {N} 道菜，快来认领 |
| ITEM_CLAIMED | {认领人} 认领了「{菜名}」 | 点单「{标题}」 |
| ITEM_DONE | {认领人} 做完了「{菜名}」 | 点单「{标题}」 |
| SHEET_COMPLETED | 点单「{标题}」已全部完成 | 🎉 收工开饭 |

## 2. API（JWT 鉴权，`/api/notifications`）

| 接口 | 说明 |
|---|---|
| `GET /api/notifications?afterId=&size=` | 游标分页（id 倒序，size 默认 20 上限 50），返回 `{items, unreadCount}` |
| `GET /api/notifications/unread-count` | 轮询用，返回 `{unreadCount}` |
| `POST /api/notifications/{id}/read` | 单条已读（原子条件 UPDATE `is_read=0→1`，仅本人） |
| `POST /api/notifications/read-all` | 全部已读（`UPDATE ... WHERE user_id=? AND is_read=0`） |

统一响应沿用 `Result<T>`；ErrorCode 新增 `NOTIFICATION_NOT_FOUND`（通知不存在或不属于当前用户）。

## 3. 事件与分发（notify 包，新包 `com.linklife.notify`）

```
notify/
  NotificationService          // 站内信落库 + 查询/已读
  NotificationController
  NotifyEventDispatcher        // @TransactionalEventListener(AFTER_COMMIT) + @Async
  event/
    SheetSharedEvent           // (circleId, sheetId, title, creatorId, itemCount)
    ItemClaimedEvent           // (sheetId, sheetTitle, itemId, dishName, claimantId, creatorId)
    ItemDoneEvent              // 同 ItemClaimedEvent
    SheetCompletedEvent        // (circleId, sheetId, title, creatorId)
  channel/
    NotificationChannel        // 接口：boolean enabled(); void send(...)
    FeishuChannel              // 全局群 Webhook
    WxSubscribeChannel         // 微信一次性订阅消息
  wechat/WxMpAccessTokenService // access_token 获取 + Caffeine 缓存（有效期减 5 分钟；40001 失效强制刷新重试一次）
```

### 分发逻辑（NotifyEventDispatcher）

1. 事件在 `OrderService.createSheet / claim / updateItemStatus(target=DONE) / completeSheet` 中 publish（各 1 行调用，事务边界与状态机不动）
2. AFTER_COMMIT 后在新事务中执行：
   - 按事件类型计算接收人（`CircleService.listMemberIds(circleId)`，排除操作者；ITEM_* 事件固定接收人为 creatorId）
   - 逐人落 `notification` 行（文案渲染）
   - 遍历 `NotificationChannel` 实现分发；**任何通道异常只 warn 日志，不上抛**
3. 飞书为全局通道：每个事件只发一条群摘要（人名+标题+菜名），不按接收人循环

### 线程池

专用 `notifyExecutor`：核心 1 / 最大 2 / 队列 200，拒绝策略 CallerRuns（通知慢不丢，退化为发送线程内执行）。

## 4. 通道实现与降级

### 配置（application.yml + compose 透传）

```yaml
link:
  notify:
    feishu:
      enabled: ${FEISHU_NOTIFY_ENABLED:false}
      webhook-url: ${FEISHU_WEBHOOK_URL:}
    wx-subscribe:
      enabled: ${WX_SUBSCRIBE_ENABLED:false}
      template-id: ${WX_SUBSCRIBE_TEMPLATE_ID:}
      page: pages/sheet-detail/sheet-detail
```

- 缺配置 → enabled 默认 false，channel 直接 skip（debug 日志），**启动不受影响、不报错**

### FeishuChannel

- `RestClient`（对齐 `auth/wechat/WeChatClient` 模式），POST `{"msg_type":"text","content":{"text":"..."}}`
- 非空 `webhook-url` 且 enabled 才发；HTTP 失败/飞书返回非 0 code → warn 日志

### WxSubscribeChannel

- POST `cgi-bin/message/subscribe/send?access_token=...`，body：`{touser: openid, template_id, page, data: {...}}`
- openid 经 `UserService` 查询；errcode 43101（用户未订阅）等业务失败只 info/warn 日志，不影响其他通知
- `WxMpAccessTokenService`：`GET cgi-bin/token?grant_type=client_credential&appid=&secret=`，Caffeine `get(key, mappingFunction)` 原子加载（`expires_in` 减 5 分钟），并发未命中只发一次请求
- 一次性订阅授权埋点不在本期范围（见决策记录）

## 5. 双端

### Web 端（Vue 3，`web/`）

- `api/notifications.js`：4 个接口封装
- 站内信页 `views/Notifications.vue`（路由 `/notifications`）：列表（未读加粗+蓝点）、点击项跳 `/sheet/{id}` 并标记单条已读、「全部已读」按钮
- 全局轮询：`App.vue` 登录态下每 30s 调 `unread-count`，导航栏"通知"入口显示红点+未读数；`document.hidden` 时暂停；401 时静默停止（复用现有请求封装刷新逻辑）

### 小程序端（`miniapp/`）

- 新页 `pages/notifications/`：`onShow` 拉列表、下拉刷新、点击项跳清单详情、未读样式同 Web
- 入口：profile 页加"我的通知"行 + 未读角标（`onShow` 查一次 unread-count）
- 不做订阅授权埋点（延后）

## 6. 测试策略

- 集成测试（`IntegrationTestBase`）：
  - 4 个事件触发后站内信落库正确、接收人排除操作者
  - 已读 / 全部已读 / 游标分页 / 未读数 API
- 单元测试：
  - FeishuChannel / WxSubscribeChannel：mock HTTP（MockRestServiceServer 或 mock RestClient），验证请求体、errcode 43101 降级、缺配置 skip
  - WxMpAccessTokenService：mock 微信 token 接口，验证缓存命中与过期刷新
- 手工验收：`cd deploy && docker compose up -d --build`；🧑 若已配飞书测试群 Webhook 则验证真实消息，未配则验证"降级不报错"；全链路：Web 建单 → 小程序认领 → 标记完成 → 观察站内信与飞书

## 7. 部署

- `deploy/docker-compose.yml` app 服务透传：`FEISHU_NOTIFY_ENABLED`、`FEISHU_WEBHOOK_URL`、`WX_SUBSCRIBE_ENABLED`、`WX_SUBSCRIBE_TEMPLATE_ID`（均带默认值，缺省可启动）
- README 与 TODO.md 更新
