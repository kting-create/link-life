# P1 点单清单 MVP 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付点单/清单/认领/分享全链路（后端 API + 集成测试 + 小程序端 + Web 端），按"后端全部完成 → 小程序 → Web → 部署收尾"顺序。

**Architecture:** 方案 A——order 模块 Service 层显式调用 `CircleService.requireMembership` 做权限校验；分享走独立免登录端点 `GET /api/share/{token}`（加入 JwtAuthFilter 白名单）。状态机简化：建单即 SHARED，首次认领自动 IN_PROGRESS，创建者手动 COMPLETED。

**Tech Stack:** Java 17 + Spring Boot 3.3.4 + MyBatis-Plus 3.5.7 + Flyway + Caffeine；微信小程序原生；Vue 3 + Vite。

**Spec:** `docs/superpowers/specs/2026-09-17-p1-order-sheet-design.md`

## Global Constraints

- 统一响应 `Result<T>`（`{"code":0,...}`），错误抛 `BusinessException(ErrorCode.X)`，由 `GlobalExceptionHandler` 转换
- 仓库路径约定：后端 `server/src/main/java/com/linklife/`，迁移 `server/src/main/resources/db/migration/`，测试 `server/src/test/java/com/linklife/`
- 测试命令：`cd server && mvn clean test`（需本机 Docker，Testcontainers MySQL 单例容器基类 `IntegrationTestBase`）
- commit 风格：conventional commits（`feat:` / `fix:` / `refactor:` / `docs:` / `chore:`），英文小写描述
- 不做：dish CRUD、推送、清单编辑/撤回、URL Link
- MyBatis-Plus 实体风格照抄 `circle/entity/Circle.java`：`@Data @TableName` + `@TableId(type = IdType.AUTO)` + LocalDateTime 字段
- Controller 风格照抄 `CircleController`：`@RestController @RequestMapping @RequiredArgsConstructor`，`UserContext.requireUserId()` 取当前用户，DTO 用 record
- 集成测试风格照抄 `CircleControllerTest`：继承 `IntegrationTestBase`，`@MockBean WeChatClient`，`login(openid)` 辅助方法拿 token

---

## Task 1: UserService 重构（解除 user↔auth 依赖）

**Files:**
- Create: `server/src/main/java/com/linklife/user/UserService.java`
- Modify: `server/src/main/java/com/linklife/auth/AuthService.java`（移除 UserMapper 直用）
- Modify: `server/src/main/java/com/linklife/user/MeController.java`
- Test: 现有测试 `server/src/test/java/com/linklife/user/MeControllerTest.java`、`server/src/test/java/com/linklife/auth/AuthControllerTest.java` 全绿即为验收

**Interfaces:**
- Consumes: `com.linklife.user.entity.User`、`com.linklife.user.mapper.UserMapper`、`com.linklife.auth.dto.UserVO`
- Produces: `UserService.getUserById(long id) -> User`、`UserService.getUserByOpenid(String openid) -> User`、`UserService.createUser(String openid, String unionid) -> User`、`UserService.toVO(User user) -> UserVO`、`UserService.getNicknames(Collection<Long> ids) -> Map<Long, String>`（Task 3/4 查认领人昵称依赖）

- [ ] **Step 1: 读现状**

读 `AuthService.java` 全文和 `MeController.java`，列出它们直接使用 `UserMapper` 的每一处（wxLogin 注册用户、toVO 映射、MeController 的查改）。

- [ ] **Step 2: 新建 UserService**

把 AuthService 中 `toVO(User)` 的映射逻辑与 MeController/AuthService 中用户读写逻辑收口进来：

```java
package com.linklife.user;

import com.linklife.auth.dto.UserVO;
import com.linklife.user.entity.User;
import com.linklife.user.mapper.UserMapper;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public User getUserById(long id) {
        return userMapper.selectById(id);
    }

    public User getUserByOpenid(String openid) {
        return userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getOpenid, openid));
    }

    public User createUser(String openid, String unionid) {
        User user = new User();
        user.setOpenid(openid);
        user.setUnionid(unionid);
        userMapper.insert(user);
        return user;
    }

    public void updateUser(User user) {
        userMapper.updateById(user);
    }

    public UserVO toVO(User user) {
        // 从 AuthService.toVO 原样搬移字段映射
    }

    public Map<Long, String> getNicknames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getNickname() == null ? "用户" + u.getId() : u.getNickname(),
                        (a, b) -> a));
    }
}
```

注意：若搬运导致 user 包 import auth 包的 `UserVO` 仍构成 user→auth 依赖，把 `UserVO` record 从 `auth/dto` 移到 `user/dto`（record 内容不变，auth 的 `UserVO` 删除并由 `AuthService`/`AuthController` 改 import）。目标终态：auth 模块不出现 `UserMapper`，user 模块不 import auth 包内类型。

- [ ] **Step 3: 改造 AuthService 与 MeController**

AuthService 注入 `UserService` 替换 `UserMapper`（wxLogin 中"按 openid 查、无则建"改调 `getUserByOpenid`/`createUser`）；MeController 的查改改用 `UserService`。对外 API 行为零变化。

- [ ] **Step 4: 验证**

Run: `cd server && mvn clean test`
Expected: 全部测试 PASS（数量与重构前一致，24 个左右）

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor: extract UserService to break user-auth package cycle"
```

---

## Task 2: Flyway V2 迁移 + order 模块实体与 Mapper

**Files:**
- Create: `server/src/main/resources/db/migration/V2__order_sheet.sql`
- Create: `server/src/main/java/com/linklife/order/entity/OrderSheet.java`
- Create: `server/src/main/java/com/linklife/order/entity/OrderItem.java`
- Create: `server/src/main/java/com/linklife/order/entity/Dish.java`
- Create: `server/src/main/java/com/linklife/order/mapper/OrderSheetMapper.java`
- Create: `server/src/main/java/com/linklife/order/mapper/OrderItemMapper.java`
- Create: `server/src/main/java/com/linklife/order/mapper/DishMapper.java`

**Interfaces:**
- Produces: MyBatis-Plus BaseMapper 三件套；实体字段与 spec 第 1 节 SQL 一一对应（`OrderSheet{id, circleId, creatorId, title, status, shareToken, createdAt, updatedAt}`、`OrderItem{id, sheetId, dishName, note, claimantId, itemStatus, createdAt, updatedAt}`、`Dish{id, circleId, userId, name, recipeId, createdAt, updatedAt}`）

- [ ] **Step 1: 写 V2 迁移 SQL**

内容严格照 spec 第 1 节三张表的 DDL（`order_sheet` 含 `UNIQUE KEY uk_share_token (share_token)` 与 `KEY idx_circle (circle_id)`；`order_item` 含 `KEY idx_sheet (sheet_id)`；`dish` 无业务索引要求）。

- [ ] **Step 2: 写实体与 Mapper**

照 `Circle.java`/`CircleMapper.java` 风格创建三个实体与三个 `extends BaseMapper<T>` 接口。

- [ ] **Step 3: 验证**

Run: `cd server && mvn clean test`
Expected: PASS（Flyway 启动时执行 V2，集成测试容器内建表成功即为验证）

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: V2 migration with order_sheet, order_item and dish tables"
```

---

## Task 3: 错误码 + 创建清单/列表/详情 API

**Files:**
- Modify: `server/src/main/java/com/linklife/common/exception/ErrorCode.java`
- Create: `server/src/main/java/com/linklife/order/dto/CreateSheetRequest.java`
- Create: `server/src/main/java/com/linklife/order/dto/ItemInput.java`
- Create: `server/src/main/java/com/linklife/order/dto/ItemVO.java`
- Create: `server/src/main/java/com/linklife/order/dto/SheetDetailVO.java`
- Create: `server/src/main/java/com/linklife/order/OrderService.java`
- Create: `server/src/main/java/com/linklife/order/OrderController.java`
- Test: `server/src/test/java/com/linklife/order/OrderControllerTest.java`

**Interfaces:**
- Consumes: `CircleService.requireMembership(long userId, long circleId)`、`UserService.getNicknames(Collection<Long>)`
- Produces:
  - ErrorCode 新增：`SHEET_NOT_FOUND(3001, "清单不存在", BAD_REQUEST)`、`ITEM_NOT_FOUND(3002, "菜品不存在", BAD_REQUEST)`、`ITEM_STATUS_INVALID(3003, "当前状态不允许该操作", BAD_REQUEST)`、`SHEET_COMPLETED(3004, "清单已收单", BAD_REQUEST)`、`NOT_ITEM_CLAIMANT(3005, "仅认领人可操作", FORBIDDEN)`、`RATE_LIMITED(4001, "请求过于频繁", TOO_MANY_REQUESTS)`（4001 本任务只加枚举，Task 6 才使用）
  - `OrderService.createSheet(long userId, String circleId, String title, List<ItemInput> items) -> SheetDetailVO`
  - `OrderService.listSheets(long userId, long circleId) -> List<SheetDetailVO>`（列表复用详情 VO，items 一并带出）
  - `OrderService.getSheet(long userId, long sheetId) -> SheetDetailVO`
  - `SheetDetailVO(long id, long circleId, long creatorId, String title, String status, String shareToken, List<ItemVO> items)`；`ItemVO(long id, String dishName, String note, Long claimantId, String claimantNickname, String itemStatus)`

- [ ] **Step 1: 写失败的测试**

```java
package com.linklife.order;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class OrderControllerTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    private String login(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private long createCircle(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/circles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试圈\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id").toString());
    }

    private long createSheet(String token, long circleId, String title) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/order/sheets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"circleId\":" + circleId + ",\"title\":\"" + title
                                + "\",\"items\":[{\"dishName\":\"番茄炒蛋\",\"note\":\"少油\"}]}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals("SHARED",
                JsonPath.read(body, "$.data.status"));
        return Long.parseLong(JsonPath.read(body, "$.data.id").toString());
    }

    @Test
    void createSheetIsSharedWithItems() throws Exception {
        String token = login("order-user-1");
        long circleId = createCircle(token);
        long sheetId = createSheet(token, circleId, "周五晚餐");

        mockMvc.perform(get("/api/order/sheets/" + sheetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("周五晚餐"))
                .andExpect(jsonPath("$.data.status").value("SHARED"))
                .andExpect(jsonPath("$.data.shareToken").isNotEmpty())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].dishName").value("番茄炒蛋"))
                .andExpect(jsonPath("$.data.items[0].note").value("少油"))
                .andExpect(jsonPath("$.data.items[0].itemStatus").value("OPEN"));
    }

    @Test
    void listSheetsByCircle() throws Exception {
        String token = login("order-user-2");
        long circleId = createCircle(token);
        createSheet(token, circleId, "清单A");
        createSheet(token, circleId, "清单B");

        mockMvc.perform(get("/api/order/sheets?circleId=" + circleId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void nonMemberCannotAccessSheet() throws Exception {
        String ownerToken = login("order-user-3");
        long circleId = createCircle(ownerToken);
        long sheetId = createSheet(ownerToken, circleId, "私密清单");

        String outsiderToken = login("order-outsider-3");
        mockMvc.perform(get("/api/order/sheets/" + sheetId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1002));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd server && mvn test -Dtest=OrderControllerTest`
Expected: 编译失败（OrderController 等不存在）

- [ ] **Step 3: 实现 ErrorCode 扩展、DTO、OrderService、OrderController**

- ErrorCode 按上面 Interfaces 清单追加枚举值
- DTO 用 record；`CreateSheetRequest` 字段校验：`@NotNull circleId`、`@NotBlank @Size(max = 64) title`、`@NotEmpty @Valid items`；`ItemInput`：`@NotBlank @Size(max = 64) dishName`、`@Size(max = 255) note`
- `OrderService.createSheet`：`requireMembership` → 组装 OrderSheet（status="SHARED"，shareToken 生成见下）→ 逐条 insert OrderItem（itemStatus="OPEN"）→ 返回 `getSheet` 同源的 VO 组装
- shareToken 生成：`SecureRandom` + base62（`A-Za-z0-9`）12 位，insert 抛 `DuplicateKeyException` 时重试，最多 3 次（参考 `CircleService.generateCode` 写法但字符集用 base62）
- `OrderService.getSheet`：selectById → 空/圈不符抛 `SHEET_NOT_FOUND`；存在则 `requireMembership(userId, sheet.getCircleId())` → 组装 VO（`claimantNickname` 用 `UserService.getNicknames` 批量查，claimantId 为 null 时 nickname 为 null）
- `listSheets`：先 `requireMembership`，再按 circleId 查 sheet 列表（`orderByDesc(OrderSheet::getId)`），批量查 items 组装（一次查所有 sheet 的 item，按 sheetId 分组，避免 N+1）
- Controller 照 `CircleController` 风格：`POST /api/order/sheets`、`GET /api/order/sheets?circleId=`、`GET /api/order/sheets/{id}`

- [ ] **Step 4: 跑测试确认通过**

Run: `cd server && mvn test -Dtest=OrderControllerTest`
Expected: 3 个测试 PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: order sheet create/list/detail APIs with membership checks"
```

---

## Task 4: 加菜 + 认领/释放/状态流转（item 状态机）

**Files:**
- Create: `server/src/main/java/com/linklife/order/dto/AddItemRequest.java`
- Create: `server/src/main/java/com/linklife/order/dto/UpdateItemStatusRequest.java`
- Modify: `server/src/main/java/com/linklife/order/OrderService.java`
- Modify: `server/src/main/java/com/linklife/order/OrderController.java`
- Test: `server/src/test/java/com/linklife/order/OrderItemFlowTest.java`

**Interfaces:**
- Consumes: Task 3 全部产物
- Produces:
  - `OrderService.addItem(long userId, long sheetId, String dishName, String note) -> ItemVO`（仅 SHARED/IN_PROGRESS 可加，COMPLETED 抛 SHEET_COMPLETED）
  - `OrderService.claim(long userId, long itemId) -> ItemVO`（仅 OPEN 可认领，否则 ITEM_STATUS_INVALID；sheet 为 SHARED 时自动转 IN_PROGRESS）
  - `OrderService.release(long userId, long itemId) -> ItemVO`（仅认领人本人，仅 CLAIMED/COOKING 可释放回 OPEN；非认领人抛 NOT_ITEM_CLAIMANT）
  - `OrderService.updateItemStatus(long userId, long itemId, String target) -> ItemVO`（仅认领人本人；target 只能是 COOKING 或 DONE；当前态 CLAIMED→COOKING/DONE，COOKING→DONE，其余组合抛 ITEM_STATUS_INVALID）
  - Controller：`POST /api/order/items`（body: `sheetId, dishName, note`）、`POST /api/order/items/{id}/claim`、`POST /api/order/items/{id}/release`、`POST /api/order/items/{id}/status`（body: `itemStatus`）

- [ ] **Step 1: 写失败的测试**

`OrderItemFlowTest` 复用 Task 3 的测试基类模式（同样的 login/createCircle/createSheet 辅助方法，复制过来）。测试用例：

```java
@Test
void claimAutoProgressesSheetAndManualStatusFlow() throws Exception {
    String alice = login("flow-alice-1");
    String bob = login("flow-bob-1");
    long circleId = createCircle(alice);
    // bob 经邀请码加入（参照 CircleControllerTest.joinWithInviteCode 写法）
    join(alice, bob, circleId);
    long sheetId = createSheet(alice, circleId, "状态机");
    long itemId = firstItemId(sheetId, alice);

    // bob 认领 → sheet 自动 IN_PROGRESS
    mockMvc.perform(post("/api/order/items/" + itemId + "/claim")
                    .header("Authorization", "Bearer " + bob))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.itemStatus").value("CLAIMED"))
            .andExpect(jsonPath("$.data.claimantNickname").isNotEmpty());
    mockMvc.perform(get("/api/order/sheets/" + sheetId)
                    .header("Authorization", "Bearer " + alice))
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

    // CLAIMED → COOKING → DONE
    mockMvc.perform(post("/api/order/items/" + itemId + "/status")
                    .header("Authorization", "Bearer " + bob)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"itemStatus\":\"COOKING\"}"))
            .andExpect(jsonPath("$.data.itemStatus").value("COOKING"));
    mockMvc.perform(post("/api/order/items/" + itemId + "/status")
                    .header("Authorization", "Bearer " + bob)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"itemStatus\":\"DONE\"}"))
            .andExpect(jsonPath("$.data.itemStatus").value("DONE"));

    // DONE 后不能再释放
    mockMvc.perform(post("/api/order/items/" + itemId + "/release")
                    .header("Authorization", "Bearer " + bob))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(3003));
}

@Test
void releaseByClaimantOnlyAndNonOpenClaimRejected() throws Exception {
    String alice = login("flow-alice-2");
    String bob = login("flow-bob-2");
    String carol = login("flow-carol-2");
    long circleId = createCircle(alice);
    join(alice, bob, circleId);
    join(alice, carol, circleId);
    long sheetId = createSheet(alice, circleId, "权限");
    long itemId = firstItemId(sheetId, alice);

    // 非 OPEN 认领拒绝：carol 直接对 OPEN 认领成功，再对同一 item 认领失败
    mockMvc.perform(post("/api/order/items/" + itemId + "/claim")
                    .header("Authorization", "Bearer " + carol))
            .andExpect(status().isOk());
    mockMvc.perform(post("/api/order/items/" + itemId + "/claim")
                    .header("Authorization", "Bearer " + bob))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(3003));

    // 非认领人不能 release / status
    mockMvc.perform(post("/api/order/items/" + itemId + "/release")
                    .header("Authorization", "Bearer " + bob))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(3005));
    mockMvc.perform(post("/api/order/items/" + itemId + "/status")
                    .header("Authorization", "Bearer " + bob)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"itemStatus\":\"COOKING\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(3005));

    // 认领人可释放回 OPEN，且 sheet 不回退（保持 IN_PROGRESS）
    mockMvc.perform(post("/api/order/items/" + itemId + "/release")
                    .header("Authorization", "Bearer " + carol))
            .andExpect(jsonPath("$.data.itemStatus").value("OPEN"));
    mockMvc.perform(get("/api/order/sheets/" + sheetId)
                    .header("Authorization", "Bearer " + alice))
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
}

@Test
void addItemToSharedAndInProgressButNotCompleted() throws Exception {
    String alice = login("flow-alice-3");
    long circleId = createCircle(alice);
    long sheetId = createSheet(alice, circleId, "加菜");
    long itemId = firstItemId(sheetId, alice);

    // SHARED 可加菜
    mockMvc.perform(post("/api/order/items")
                    .header("Authorization", "Bearer " + alice)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sheetId\":" + sheetId + ",\"dishName\":\"可乐鸡翅\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.itemStatus").value("OPEN"));

    // 认领后 IN_PROGRESS 仍可加菜
    mockMvc.perform(post("/api/order/items/" + itemId + "/claim")
                    .header("Authorization", "Bearer " + alice))
            .andExpect(status().isOk());
    mockMvc.perform(post("/api/order/items")
                    .header("Authorization", "Bearer " + alice)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sheetId\":" + sheetId + ",\"dishName\":\"凉拌黄瓜\"}"))
            .andExpect(status().isOk());
}
```

辅助方法 `firstItemId(sheetId, token)`：GET 详情取 `$.data.items[0].id`；`join(owner, member, circleId)`：从建圈响应取邀请码再 join（照抄 CircleControllerTest）。

- [ ] **Step 2: 跑测试确认失败**

Run: `cd server && mvn test -Dtest=OrderItemFlowTest`
Expected: 编译失败（方法不存在）

- [ ] **Step 3: 实现**

- Service 方法实现按 Interfaces 描述的状态机规则；每次写 item 前重查 sheet 判断 COMPLETED（抛 `SHEET_COMPLETED`）
- `claim`/`release`/`updateItemStatus` 的权限与状态校验全部在 Service 内做；claim 时若 `sheet.status == "SHARED"` 则同事务内 `UPDATE order_sheet SET status='IN_PROGRESS' WHERE id=? AND status='SHARED'`（用 MyBatis-Plus `LambdaUpdateWrapper` 带条件，天然防并发重复流转）
- `UpdateItemStatusRequest` record：`@NotBlank @Pattern(regexp = "COOKING|DONE") String itemStatus`

- [ ] **Step 4: 跑测试确认通过**

Run: `cd server && mvn test -Dtest=OrderItemFlowTest`
Expected: 3 个测试 PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: item claim/release/status flow with sheet auto progression"
```

---

## Task 5: 收单 + 分享只读端点

**Files:**
- Create: `server/src/main/java/com/linklife/order/ShareController.java`
- Modify: `server/src/main/java/com/linklife/common/security/JwtAuthFilter.java`
- Modify: `server/src/main/java/com/linklife/order/OrderService.java`
- Test: `server/src/test/java/com/linklife/order/SheetCompleteAndShareTest.java`

**Interfaces:**
- Consumes: Task 3/4 产物
- Produces:
  - `OrderService.completeSheet(long userId, long sheetId)`（仅创建者；重复收单抛 SHEET_COMPLETED；COMPLETED 后一切写操作被 Task 4 的校验拒绝）
  - `ShareService.getByToken(String token) -> SheetDetailVO`（与圈内详情同一 VO；token 无效抛 SHEET_NOT_FOUND）
  - Controller：`POST /api/order/sheets/{id}/complete`、`GET /api/share/{token}`

- [ ] **Step 1: 写失败的测试**

`SheetCompleteAndShareTest`（辅助方法同前）：

```java
@Test
void onlyCreatorCanCompleteAndCompletedSheetRejectsWrites() throws Exception {
    String alice = login("complete-alice-1");
    String bob = login("complete-bob-1");
    long circleId = createCircle(alice);
    join(alice, bob, circleId);
    long sheetId = createSheet(alice, circleId, "收单");
    long itemId = firstItemId(sheetId, alice);

    // 非创建者不能收单
    mockMvc.perform(post("/api/order/sheets/" + sheetId + "/complete")
                    .header("Authorization", "Bearer " + bob))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(3005));

    // 创建者收单成功
    mockMvc.perform(post("/api/order/sheets/" + sheetId + "/complete")
                    .header("Authorization", "Bearer " + alice))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"));

    // 重复收单拒绝
    mockMvc.perform(post("/api/order/sheets/" + sheetId + "/complete")
                    .header("Authorization", "Bearer " + alice))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(3004));

    // 收单后加菜/认领/改状态全部拒绝
    mockMvc.perform(post("/api/order/items")
                    .header("Authorization", "Bearer " + alice)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"sheetId\":" + sheetId + ",\"dishName\":\"迟到菜\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(3004));
    mockMvc.perform(post("/api/order/items/" + itemId + "/claim")
                    .header("Authorization", "Bearer " + alice))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(3004));
}

@Test
void shareEndpointIsPublicAndReadOnly() throws Exception {
    String alice = login("share-alice-1");
    long circleId = createCircle(alice);
    long sheetId = createSheet(alice, circleId, "分享清单");
    String shareToken = getShareToken(sheetId, alice); // GET 详情取 $.data.shareToken

    // 无 Authorization 头可读（免登录）
    mockMvc.perform(get("/api/share/" + shareToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("分享清单"))
            .andExpect(jsonPath("$.data.items[0].dishName").value("番茄炒蛋"));

    // 无效 token
    mockMvc.perform(get("/api/share/NOPE"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(3001));
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd server && mvn test -Dtest=SheetCompleteAndShareTest`
Expected: 编译失败

- [ ] **Step 3: 实现**

- `completeSheet`：重查 sheet（空抛 SHEET_NOT_FOUND）→ `creatorId != userId` 抛 NOT_ITEM_CLAIMANT（语义"仅创建者可操作"复用 3005）→ 已 COMPLETED 抛 SHEET_COMPLETED → update status
- `ShareController`：`@RestController`，`GET /api/share/{token}` 直接调 `ShareService.getByToken`（Service 放 order 包，方法内部只按 shareToken 查 sheet + items 组装 VO，无任何 requireMembership）
- `JwtAuthFilter.shouldNotFilter` 增加：`if (path.startsWith("/api/share/")) return true;`
- 收单后写拒绝的校验点在 Task 4 已实现（每次写 item 前查 sheet），本任务只需保证 complete 后状态正确

- [ ] **Step 4: 跑测试确认通过**

Run: `cd server && mvn test -Dtest=SheetCompleteAndShareTest`
Expected: 2 个测试 PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: sheet completion and public read-only share endpoint"
```

---

## Task 6: 限流（Caffeine 计数器）

**Files:**
- Create: `server/src/main/java/com/linklife/common/ratelimit/RateLimiter.java`
- Modify: `server/src/main/java/com/linklife/auth/AuthController.java`（bind 端点）
- Modify: `server/src/main/java/com/linklife/circle/CircleController.java`（join 端点）
- Test: `server/src/test/java/com/linklife/common/ratelimit/RateLimitTest.java`

**Interfaces:**
- Consumes: ErrorCode.RATE_LIMITED(4001)
- Produces: `RateLimiter.check(String key)`——超阈值抛 `BusinessException(RATE_LIMITED)`；每 key 每分钟 5 次，Caffeine `expireAfterWrite(1, MINUTES)` 计数窗口

- [ ] **Step 1: 写失败的测试**

单测 `RateLimitTest`（不继承 IntegrationTestBase，直接 new）：

```java
package com.linklife.common.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linklife.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class RateLimitTest {

    @Test
    void sixthCallWithinWindowIsRejected() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.check("user1:bind"));
        }
        assertThrows(BusinessException.class, () -> limiter.check("user1:bind"));
        // 不同 key 互不影响
        assertDoesNotThrow(() -> limiter.check("user2:bind"));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd server && mvn test -Dtest=RateLimitTest`
Expected: 编译失败（RateLimiter 不存在）

- [ ] **Step 3: 实现**

```java
package com.linklife.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private static final int LIMIT = 5;

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    public void check(String key) {
        AtomicInteger count = counters.get(key, k -> new AtomicInteger());
        if (count.incrementAndGet() > LIMIT) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }
}
```

确认 `pom.xml` 已有 caffeine 依赖（P0 引入过；若无需加 `com.github.benmanes.caffeine:caffeine`）。接入两个端点：bind 处理方法与 `CircleController.join` 开头加 `rateLimiter.check(UserContext.requireUserId() + ":join")` 与对应 bind key（bind 端点若为免登录路径则用 IP 或请求体字段做 key——读 AuthController 后按实际情况选 userId 或 IP，写清注释）。

- [ ] **Step 4: 跑测试确认通过 + 全量回归**

Run: `cd server && mvn clean test`
Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: caffeine-based rate limiting for bind and invite-code endpoints"
```

---

## Task 7: 微信小程序工程骨架 + 登录 + 请求封装

**Files:**
- Create: `miniapp/project.config.json`、`miniapp/app.json`、`miniapp/app.js`、`miniapp/app.wxss`
- Create: `miniapp/utils/request.js`
- Create: `miniapp/pages/login/login.js|wxml|wxss|json`
- Create: `miniapp/sitemap.json`

**Interfaces:**
- Consumes: `POST /api/auth/wx-login`（body `{code}`，返回 `data.accessToken/refreshToken`）
- Produces: `request(path, {method, data}) -> Promise<data>`——自动带 `Authorization: Bearer`、401 时用 refreshToken 调 `POST /api/auth/refresh` 重试一次、失败清 storage 跳登录页；`BASE_URL` 常量（开发环境 `http://localhost:8080`，上线改 https 域名）

- [ ] **Step 1: 建工程骨架**

`app.json` 注册页面：`pages/login/login`、`pages/circle/circle`、`pages/order-create/order-create`、`pages/sheet-list/sheet-list`、`pages/sheet-detail/sheet-detail`、`pages/profile/profile`；`window` 配置标题"Link-Life"。`project.config.json` 的 appid 填 `touristappid`（测试号），真机时替换。

- [ ] **Step 2: 写请求封装**

```javascript
const BASE_URL = 'http://localhost:8080';

function raw(path, method, data, token) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + path,
      method,
      data,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success: (res) => {
        if (res.data.code === 0) resolve(res.data.data);
        else reject(res.data);
      },
      fail: reject,
    });
  });
}

function refresh() {
  return raw('/api/auth/refresh', 'POST',
    { refreshToken: wx.getStorageSync('refreshToken') })
    .then((d) => {
      wx.setStorageSync('accessToken', d.accessToken);
      wx.setStorageSync('refreshToken', d.refreshToken);
      return d.accessToken;
    });
}

function request(path, options = {}) {
  const token = wx.getStorageSync('accessToken');
  return raw(path, options.method || 'GET', options.data, token).catch((err) => {
    if (err && err.code === 2002) {
      return refresh().then((t) => raw(path, options.method || 'GET', options.data, t));
    }
    throw err;
  });
}

module.exports = { request, BASE_URL };
```

注意：后端 401（filter 层拒绝）时 `wx.request` 仍走 success 回调但 HTTP 401 且 body 为 `{"code":2002}`，上述按 body code 判断已覆盖；`refresh` 失败时清 storage 并 `wx.reLaunch('/pages/login/login')`（在 catch 里补）。

- [ ] **Step 3: 登录页**

`login.js`：`wx.login` 拿 code → `POST /api/auth/wx-login` → 存双 token → `wx.reLaunch('/pages/circle/circle')`。wxml 一个按钮"微信一键登录"。登录态判断放 `app.js` onLaunch：有 accessToken 则直接进圈子页。

- [ ] **Step 4: 手工验证**

微信开发者工具导入 `miniapp/`，后端本地起 `mvn spring-boot:run`（或 compose），点登录按钮确认拿到 token 并跳转（圈子页可先空白）。AppID 未就绪时用测试号即可。

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: miniapp skeleton with login page and jwt request wrapper"
```

---

## Task 8: 小程序圈子页 + 我的页

**Files:**
- Create: `miniapp/pages/circle/circle.js|wxml|wxss|json`
- Create: `miniapp/pages/profile/profile.js|wxml|wxss|json`
- Modify: `miniapp/app.json`（如 Task 7 未注册全）

**Interfaces:**
- Consumes: `GET /api/circles`、`POST /api/circles`（body `{name}`）、`POST /api/circles/join`（body `{inviteCode}`）、`GET /api/circles/{id}/members`、`GET /api/me`、`PUT /api/me`、`POST /api/auth/binding-code`、`GET /api/auth/refresh`
- Produces: 圈子列表页为入口（点击圈子 → `sheet-list?circleId=`）；全局选圈状态存 storage（`currentCircle`）供点单页/清单页用

- [ ] **Step 1: 圈子页**

- 列表展示我的圈子（名称 + 邀请码）；"建圈"按钮弹输入框 → POST；"加入圈子"按钮弹输入框填邀请码 → POST join
- 点击某圈子：`wx.setStorageSync('currentCircle', circle)` 后跳 `sheet-list`
- 顶部 tab 切到成员视图：GET members 展示昵称/角色

- [ ] **Step 2: 我的页**

- GET /api/me 展示昵称头像；修改调 PUT /api/me
- "生成绑定码"按钮 → POST /api/auth/binding-code → 展示 6 位码 + 有效期文案（Web 端输入用）

- [ ] **Step 3: 手工验证**

开发者工具走通：登录 → 建圈 → 列表可见 → 第二个测试号加入 → 成员列表两人 → 我的页改昵称、生成绑定码。

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: miniapp circle and profile pages"
```

---

## Task 9: 小程序点单页 + 清单列表/详情页（含分享与认领）

**Files:**
- Create: `miniapp/pages/order-create/order-create.js|wxml|wxss|json`
- Create: `miniapp/pages/sheet-list/sheet-list.js|wxml|wxss|json`
- Create: `miniapp/pages/sheet-detail/sheet-detail.js|wxml|wxss|json`

**Interfaces:**
- Consumes: `POST /api/order/sheets`、`GET /api/order/sheets?circleId=`、`GET /api/order/sheets/{id}`、`POST /api/order/items`、`POST /api/order/items/{id}/claim|release|status`、`POST /api/order/sheets/{id}/complete`、`GET /api/share/{token}`
- Produces: 分享路径 `/pages/sheet-detail/sheet-detail?token={shareToken}`；详情页 onLoad 同时兼容 `id=`（圈内）与 `token=`（分享落地）两种参数

- [ ] **Step 1: 点单页**

动态表单：菜名（必填）+ 备注（选填），"+"增行；提交组装 items 数组 POST sheets → 成功跳详情页（带 id）。

- [ ] **Step 2: 清单列表页**

读 `currentCircle` → GET sheets 列表；每项显示标题、状态 badge（SHARED=分享中 / IN_PROGRESS=进行中 / COMPLETED=已收单）、认领进度（`x/y 已认领`）；"+"按钮跳点单页。

- [ ] **Step 3: 清单详情页**

- onLoad：有 `token` 参数走 GET /api/share/{token} 渲染只读视图；有 `id` 参数走圈内详情
- 圈内视图按 item 状态与本人关系渲染操作按钮：OPEN→"认领"；本人认领且 CLAIMED/COOKING→"开始烹饪(COOKING)"/"完成(DONE)"/"释放"；创建者且非 COMPLETED→"收单"
- 分享：`onShareAppMessage` 返回 `{ title: sheet.title, path: '/pages/sheet-detail/sheet-detail?token=' + shareToken }`
- 落地页升级逻辑：只读渲染后若本地有 accessToken，尝试 GET 圈内详情（用返回的 id），成功则切换为可操作视图（失败保持只读）

- [ ] **Step 4: 手工验证**

双测试号模拟：A 建单分享 → B 点卡片看只读 → B 登录后进详情可认领 → 状态流转到 DONE → A 收单 → 收单后按钮消失。开发者工具"编译模式"直接带 `?token=` 参数模拟分享落地。

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: miniapp order creation and sheet detail with share and claim"
```

---

## Task 10: Web 端骨架 + 绑定码登录 + 圈子/点单/清单页

**Files:**
- Create: `web/package.json`、`web/vite.config.js`、`web/index.html`、`web/src/main.js`、`web/src/App.vue`、`web/src/router.js`
- Create: `web/src/api/request.js`
- Create: `web/src/views/Login.vue`、`Circles.vue`、`SheetDetail.vue`
- Modify: 根 `web-dist/`（build 产物，.gitignore 视 P0 约定处理）

**Interfaces:**
- Consumes: 与小程序同一套 API；登录用 `POST /api/auth/bind`（body `{code}`，返回双 token）——实现前先读 `AuthController` 确认实际路径与参数名
- Produces: axios 实例（401 刷新重试同小程序逻辑）；路由 `/login`、`/circles`、`/sheets/:id`、`/s/:token`；`npm run build` outDir 指向仓库根 `web-dist/`

- [ ] **Step 1: 脚手架**

`npm create vite@latest . -- --template vue`（在 `web/` 内）或手写最小工程；`vite.config.js` 设 `build.outDir: '../web-dist'`、`emptyOutDir: true`；router 用 vue-router4 + history 模式。`src/api/request.js` 用 axios 实现与小程序一致的 JWT/刷新逻辑（token 存 localStorage）。

- [ ] **Step 2: 登录页 + 圈子页 + 清单详情页**

- Login.vue：6 位码输入 → POST bind → 存 token → 跳 /circles（绑定码从小程序"我的页"生成）
- Circles.vue：圈子列表 + 成员 + 建圈/加入（对齐小程序功能）；选中圈子内列出清单，点击进 `/sheets/:id`；含"发起点单"表单（同点单页逻辑，可做成 Circles 页内弹层，Web 端无需独立路由）
- SheetDetail.vue：与小程序详情页能力一致（认领/释放/COOKING/DONE/收单按钮按权限渲染）
- 顶部导航显示当前用户昵称（GET /api/me）+ 退出

- [ ] **Step 3: 手工验证**

`npm run dev` 本地联调（vite proxy 或 BASE_URL 直连 localhost:8080）：绑定码登录 → 建圈 → 点单 → 认领 → 收单全链路。

- [ ] **Step 4: 构建验证**

Run: `cd web && npm run build`
Expected: 产物出现在 `web-dist/`

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: web app with binding-code login, circles and sheet pages"
```

---

## Task 11: Web 分享只读页 `/s/:token`

**Files:**
- Create: `web/src/views/ShareView.vue`
- Modify: `web/src/router.js`（加 `/s/:token` 免鉴权路由）

**Interfaces:**
- Consumes: `GET /api/share/{token}`
- Produces: 未登录可访问的只读清单页（标题、状态、菜品、认领人昵称）

- [ ] **Step 1: 实现**

ShareView.vue：onMounted 取 `route.params.token` 调 GET share，渲染只读视图；无效 token 显示"清单不存在或已失效"；页面底部固定提示"微信内搜索小程序 Link-Life 可认领菜品"。

- [ ] **Step 2: 验证**

Run: `cd web && npm run build && npx vite preview`
浏览器无痕窗口（无 token）访问 `http://localhost:4173/s/{有效token}` 确认可读、`/s/badtoken` 显示失效文案。

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: public read-only share page for web"
```

---

## Task 12: 部署收尾

**Files:**
- Modify: `deploy/nginx.conf`
- Modify: `README.md`
- Modify: `docs/PROJECT-STATUS.md`、`docs/TODO.md`

**Interfaces:**
- Consumes: `web-dist/` 产物、`/api/share/` 免登录路径

- [ ] **Step 1: 核对 nginx.conf**

读 `deploy/nginx.conf`，确认：a) 静态根指向容器内 `web-dist` 挂载路径且 SPA fallback（`location / { try_files $uri /index.html; }` 覆盖 `/s/*`）；b) `/api/` 反代到 app（`/api/share/` 属于 `/api/` 前缀天然覆盖，无需单独规则）。缺啥补啥。

- [ ] **Step 2: 本地全栈冒烟**

Run: `cd deploy && docker compose up -d --build && curl -s http://localhost/api/health && curl -s http://localhost/api/share/badtoken`
Expected: health 返回 code 0；share 返回 `{"code":3001,...}`（证明免登录路径 + 错误码贯通）

- [ ] **Step 3: 文档更新**

README 增加 P1 功能说明与 web 构建命令；`docs/TODO.md` 勾选 1.1~1.5 已完成项（保留 🧑 人工项）；`docs/PROJECT-STATUS.md` 路线图 P1 置 ✅、更新"下一步"指向 P2、已知延后项同步（限流已做→删除该条）。

- [ ] **Step 4: 全量回归 + Commit**

Run: `cd server && mvn clean test`
Expected: 全部 PASS

```bash
git add -A && git commit -m "chore: nginx share route, docs update for P1 completion"
```

- [ ] **Step 5: 推送**

```bash
git push origin main
```
