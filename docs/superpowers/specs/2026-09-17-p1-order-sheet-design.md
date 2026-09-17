# P1 点单清单 MVP 设计（spec 增量）

日期：2026-09-17
状态：已与需求方确认定稿
父文档：`docs/superpowers/specs/2026-09-16-link-life-design.md`（第 4 节为数据模型与分享机制的设计基线，本文档为其 P1 实施增量）

## 0. 范围与决策记录

- **交付顺序**：后端全部完成（含集成测试）→ 小程序端 → Web 端 → 部署收尾
- **dish 表**：V2 建表但无 CRUD 界面与业务引用，P3 接 AI 菜谱时启用
- **状态机简化**：不保留 DRAFT 草稿态，建单即 SHARED；首次认领自动流转 IN_PROGRESS；创建者手动收单 COMPLETED（不限全部 DONE）
- **认领规则**：一人可认领多道菜；认领/释放仅限本人操作；COOKING/DONE 由认领人手动标记；COMPLETED 仅创建者可触发
- **小程序开发条件**：AppID/AppSecret 未到位前，用微信开发者工具测试号开发，登录接口预留 wx.login 流程，不阻塞开发
- **权限实现方案（方案 A）**：Service 层显式调用 `CircleService.requireMembership` 校验；分享只读走独立免登录端点。不引入 Spring Security 方法级鉴权，不做数据冗余

## 1. 数据模型（Flyway V2）

```sql
CREATE TABLE order_sheet (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    circle_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    title VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL COMMENT 'SHARED / IN_PROGRESS / COMPLETED',
    share_token CHAR(12) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_share_token (share_token),
    KEY idx_circle (circle_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sheet_id BIGINT NOT NULL,
    dish_name VARCHAR(64) NOT NULL,
    note VARCHAR(255) NULL,
    claimant_id BIGINT NULL,
    item_status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / CLAIMED / COOKING / DONE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_sheet (sheet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dish (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    circle_id BIGINT NULL,
    user_id BIGINT NULL,
    name VARCHAR(64) NOT NULL,
    recipe_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

说明：

- DRAFT 状态不落库，`order_sheet.status` 无 DRAFT 取值
- `share_token`：SecureRandom base62 12 位，插入时碰撞则重试（概率极低，最多重试 3 次）
- dish 表 `circle_id`/`user_id` 均可空（圈子共享或个人档案），P1 不写不改

## 2. 后端 API 设计

统一响应 `Result<T>`，模块分包 `com.linklife.order`（controller/service/entity/mapper/dto）。

| 端点 | 权限 | 说明 |
|---|---|---|
| `POST /api/order/sheets` | 圈内成员 | body: `circleId, title, items[{dishName, note}]`；创建即 SHARED；返回含 share_token |
| `GET /api/order/sheets?circleId=` | 圈内成员 | 该圈子清单列表（含状态与认领进度摘要） |
| `GET /api/order/sheets/{id}` | 圈内成员 | 详情含 items 及认领人昵称 |
| `POST /api/order/items` | 圈内成员 | 向已有清单加菜；仅 SHARED / IN_PROGRESS 可加 |
| `POST /api/order/items/{id}/claim` | 圈内成员 | 认领；若 sheet 为 SHARED 则自动转 IN_PROGRESS；一人可认领多道 |
| `POST /api/order/items/{id}/release` | 仅认领人本人 | 释放回 OPEN（CLAIMED/COOKING 可释放） |
| `POST /api/order/items/{id}/status` | 仅认领人本人 | body: `COOKING` 或 `DONE`；仅认领人可改自己认领的菜 |
| `POST /api/order/sheets/{id}/complete` | 仅创建者 | → COMPLETED；不限 items 全部 DONE；收单后禁止再加菜/认领/改状态 |
| `GET /api/share/{token}` | **免登录** | 只读视图：sheet + items + 认领人昵称；不含手机号等敏感字段 |

规则细节：

- 写操作权限统一前置调用 `CircleService.requireMembership(userId, circleId)`（通过 sheet 反查 circle）
- `GET /api/share/{token}` 路径加入 `JwtAuthFilter` 白名单；返回体与圈内详情共用 VO，但入口只按 token 查，无越权面
- COMPLETED 状态的 sheet：加菜、认领、释放、改 item 状态均拒绝（`SHEET_COMPLETED`）
- item 状态流转合法性在 Service 层校验：OPEN→CLAIMED（claim）、CLAIMED/COOKING→OPEN（release）、CLAIMED→COOKING→DONE 或 CLAIMED→DONE（status 接口按目标态校验当前态）
- claim 仅对 OPEN 菜品生效，对已认领菜品调用返回 `ITEM_STATUS_INVALID`

### 错误码（新增，续接 ErrorCode 枚举）

| 码 | 名称 | HTTP |
|---|---|---|
| 3001 | SHEET_NOT_FOUND 清单不存在 | 400 |
| 3002 | ITEM_NOT_FOUND 菜品不存在 | 400 |
| 3003 | ITEM_STATUS_INVALID 当前状态不允许该操作 | 400 |
| 3004 | SHEET_COMPLETED 清单已收单 | 400 |
| 3005 | NOT_ITEM_CLAIMANT 仅认领人可操作 | 403 |
| 4001 | RATE_LIMITED 请求过于频繁 | 429 |

### 限流（TODO 遗留项，P1 一并处理）

- 覆盖端点：`/api/auth/bind`、圈子邀请码加入端点
- 实现：Caffeine 计数器，key = userId + 端点，窗口 1 分钟，阈值 5 次；超限抛 `RATE_LIMITED`
- 进程内实现即可（单机部署，重启清零可接受）

## 3. 重构前置：UserService（解除 user↔auth 循环依赖）

- P1 第一个 commit：新建 `com.linklife.user.UserService`，收敛跨模块用户查询（昵称批量查询、openid 查用户等）
- auth 模块改为依赖 `UserService`，order 模块查认领人昵称也走它
- 不改任何对外 API 行为，现有测试全绿为验收标准

## 4. 前端双端

### 微信小程序 `miniapp/`（新建，原生框架）

- 页面：
  - `login`：wx.login → POST /api/auth/wx-login；AppID 未到位时用测试号，接口层按真实流程实现
  - `circle`：建圈、邀请码加入、成员列表
  - `order-create`：动态添加菜名 + 备注，提交生成清单
  - `sheet-list` / `sheet-detail`：列表与详情；详情页支持认领/释放/标记完成/收单（按权限显示）
  - `profile`：昵称头像修改、生成绑定码
- 分享：`sheet-detail` 的 `onShareAppMessage` 携带 `?token={share_token}`；落地页读 token 先走 `/api/share/{token}` 渲染，若本地有登录态且为圈内成员则升级为可操作视图
- 请求封装 `utils/request.js`：JWT 存 storage、401 时用 refresh token 刷新并重试一次、统一错误提示

### Web 端 `web/`（新建，Vue 3 + Vite）

- 路由：`/login`（绑定码登录）、`/circles`、`/circles/:id`（点单 + 清单列表）、`/sheets/:id`（清单详情，操作与小程序对齐）、`/s/:token`（公开只读，免鉴权路由）
- 请求封装与小程序同规则（axios + JWT interceptor）
- `npm run build` 产物输出到仓库根 `web-dist/`（nginx 已挂载该目录）

## 5. 测试与收尾

- 后端集成测试（Testcontainers 基类 `IntegrationTestBase`）：
  - 建单→分享→认领→完成全链路
  - 状态机全路径：SHARED→IN_PROGRESS 自动流转、COMPLETED 后拒绝操作、item 非法流转拒绝
  - 权限：圈外用户 403、非认领人 release/status 403、非创建者 complete 403
  - `/api/share/{token}` 免登录可读、无效 token 复用 `SHEET_NOT_FOUND(3001)` 返回
  - 限流：超阈值返回 4001
- 收尾清单：
  - `deploy/nginx.conf` 确认 `/s/` 前端路由与 `web-dist` 挂载生效
  - README 更新（P1 功能说明、web 构建）
  - `mvn clean test` 全绿 → 更新 `docs/PROJECT-STATUS.md` 路线图（P1 ✅）→ 合并推送

## 6. 非目标（本阶段明确不做）

- dish CRUD 与菜品档案界面（P3）
- 推送/通知（P2）
- 清单编辑标题/删单/撤回（迭代项，记入随手记录区）
- URL Link/Short Link（个人主体不可用）
