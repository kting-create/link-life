# P2 推送模块实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付站内信（4 个事件驱动）+ 飞书 Webhook + 微信订阅消息通道（可配置开关、缺配置降级）+ Web/小程序站内通知页，按"后端全部完成 → Web → 小程序 → 部署收尾"顺序。

**Architecture:** 方案 A——order 模块在 `createSheet`/`claim`/`updateItemStatus(DONE)`/`completeSheet` 中 publish 领域事件；`notify` 包 `NotifyEventDispatcher` 以 `@TransactionalEventListener(AFTER_COMMIT)` + `@Async("notifyExecutor")` 异步落站内信并分发到通道。通道分两类接口：`PersonalChannel`（按通知逐条发，微信订阅消息）与 `BroadcastChannel`（每事件发一条摘要，飞书群 Webhook），Spring 自动收集 `List<PersonalChannel>` / `List<BroadcastChannel>`，任何通道异常只 warn 日志。

**Tech Stack:** Java 17 + Spring Boot 3.3.4 + MyBatis-Plus 3.5.7 + Flyway + Caffeine + RestClient；Vue 3 + Vite；微信小程序原生。

**Spec:** `docs/superpowers/specs/2026-09-17-p2-notification-design.md`

## Global Constraints

- 统一响应 `Result<T>`（`{"code":0,...}`），错误抛 `BusinessException(ErrorCode.X)`，由 `GlobalExceptionHandler` 转换
- 仓库路径约定：后端 `server/src/main/java/com/linklife/`，迁移 `server/src/main/resources/db/migration/`，测试 `server/src/test/java/com/linklife/`
- 测试命令：`cd server && mvn clean test`（需本机 Docker，Testcontainers MySQL 单例容器基类 `IntegrationTestBase`；本机需 `server/src/test/resources/docker-java.properties`）
- commit 风格：conventional commits（`feat:` / `fix:` / `refactor:` / `docs:` / `chore:`），英文小写描述
- 所有通道开关默认 false、缺配置可启动不报错；通道内任何异常只记日志不上抛
- 实体风格照抄 `user/entity/User.java`：`@Data @TableName` + `@TableId(type = IdType.AUTO)` + LocalDateTime 字段
- Controller 风格照抄 `order/OrderController.java`：`@RestController @RequestMapping @RequiredArgsConstructor`，`UserContext.requireUserId()` 取当前用户，DTO 用 record
- 配置属性类风格照抄 `auth/wechat/WeChatProperties.java`：`@Data @Component @ConfigurationProperties`
- 集成测试风格照抄 `OrderControllerTest`：继承 `IntegrationTestBase`，`@MockBean WeChatClient`，`login(openid)` 辅助方法拿 token
- RestClient 构造风格照抄 `auth/wechat/WeChatClient.java`（构造器注入 `RestClient.Builder`，测试用 `MockRestServiceServer.bindTo(builder)` 打桩）
- 不做：Bark/Server酱、小程序订阅授权埋点（`wx.requestSubscribeMessage`）、圈级通道配置

---

## Task 1: Flyway V3 + 站内信实体/Mapper + NotificationService + API

**Files:**
- Create: `server/src/main/resources/db/migration/V3__notification.sql`
- Create: `server/src/main/java/com/linklife/notify/entity/Notification.java`
- Create: `server/src/main/java/com/linklife/notify/mapper/NotificationMapper.java`
- Create: `server/src/main/java/com/linklife/notify/dto/NotificationVO.java`
- Create: `server/src/main/java/com/linklife/notify/dto/NotificationPageVO.java`
- Create: `server/src/main/java/com/linklife/notify/dto/UnreadCountVO.java`
- Create: `server/src/main/java/com/linklife/notify/NotificationService.java`
- Create: `server/src/main/java/com/linklife/notify/NotificationController.java`
- Modify: `server/src/main/java/com/linklife/common/exception/ErrorCode.java`
- Test: `server/src/test/java/com/linklife/notify/NotificationApiTest.java`

**Interfaces:**
- Consumes: `Result.ok(...)`、`UserContext.requireUserId()`
- Produces（Task 2 的 Dispatcher 依赖）:
  - `NotificationService.persist(long userId, String type, String title, String content, Long sheetId, Long circleId) -> Notification`
  - `Notification{id, userId, type, title, content, sheetId, circleId, isRead(int), createdAt}`
  - `ErrorCode.NOTIFICATION_NOT_FOUND(3006, "通知不存在", HttpStatus.BAD_REQUEST)`
- Produces（双端依赖的 API，返回 `data` 结构）:
  - `GET /api/notifications?afterId=&size=` → `{items: [{id, type, title, content, sheetId, circleId, read, createdAt}], unreadCount}`
  - `GET /api/notifications/unread-count` → `{unreadCount}`
  - `POST /api/notifications/{id}/read` → `{}`（非本人/不存在抛 3006）
  - `POST /api/notifications/read-all` → `{}`

- [ ] **Step 1: 写 V3 迁移 SQL**

`V3__notification.sql` 内容严格照 spec 第 1 节：

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

- [ ] **Step 2: 写实体、Mapper、DTO、ErrorCode**

```java
package com.linklife.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private Long sheetId;
    private Long circleId;
    private Integer isRead;
    private LocalDateTime createdAt;
}
```

```java
package com.linklife.notify.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linklife.notify.entity.Notification;

public interface NotificationMapper extends BaseMapper<Notification> {
}
```

DTO 三个 record：

```java
package com.linklife.notify.dto;

public record NotificationVO(long id, String type, String title, String content,
                             Long sheetId, Long circleId, boolean read, String createdAt) {
}
```

```java
package com.linklife.notify.dto;

import java.util.List;

public record NotificationPageVO(List<NotificationVO> items, long unreadCount) {
}
```

```java
package com.linklife.notify.dto;

public record UnreadCountVO(long unreadCount) {
}
```

ErrorCode 追加（在 `RATE_LIMITED` 之前，保持 3xxx 段连续）：

```java
    NOTIFICATION_NOT_FOUND(3006, "通知不存在", HttpStatus.BAD_REQUEST),
```

注意 `@MapperScan("com.linklife.**.mapper")` 已覆盖 notify.mapper，无需改应用类。

- [ ] **Step 3: 写 NotificationService**

```java
package com.linklife.notify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.notify.dto.NotificationPageVO;
import com.linklife.notify.dto.NotificationVO;
import com.linklife.notify.entity.Notification;
import com.linklife.notify.mapper.NotificationMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMapper notificationMapper;

    @Transactional
    public Notification persist(long userId, String type, String title, String content,
                                Long sheetId, Long circleId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setSheetId(sheetId);
        n.setCircleId(circleId);
        notificationMapper.insert(n);
        return n;
    }

    public NotificationPageVO list(long userId, Long afterId, int size) {
        int limit = Math.min(Math.max(size, 1), 50);
        List<Notification> rows = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .lt(afterId != null, Notification::getId, afterId)
                        .orderByDesc(Notification::getId)
                        .last("LIMIT " + limit));
        long unread = unreadCount(userId);
        List<NotificationVO> items = rows.stream().map(n -> new NotificationVO(
                n.getId(), n.getType(), n.getTitle(), n.getContent(),
                n.getSheetId(), n.getCircleId(), n.getIsRead() != null && n.getIsRead() == 1,
                n.getCreatedAt() == null ? null : n.getCreatedAt().toString())).toList();
        return new NotificationPageVO(items, unread);
    }

    public long unreadCount(long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return count == null ? 0 : count;
    }

    @Transactional
    public void markRead(long userId, long id) {
        int updated = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
        if (updated == 0) {
            Long exists = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                    .eq(Notification::getId, id)
                    .eq(Notification::getUserId, userId));
            if (exists == null || exists == 0) {
                throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
            }
        }
    }

    @Transactional
    public void markAllRead(long userId) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }
}
```

- [ ] **Step 4: 写 NotificationController**

```java
package com.linklife.notify;

import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.notify.dto.NotificationPageVO;
import com.linklife.notify.dto.UnreadCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Result<NotificationPageVO> list(@RequestParam(required = false) Long afterId,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.ok(notificationService.list(UserContext.requireUserId(), afterId, size));
    }

    @GetMapping("/unread-count")
    public Result<UnreadCountVO> unreadCount() {
        return Result.ok(new UnreadCountVO(
                notificationService.unreadCount(UserContext.requireUserId())));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable long id) {
        notificationService.markRead(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(UserContext.requireUserId());
        return Result.ok();
    }
}
```

- [ ] **Step 5: 写失败的集成测试**

测试直接用 Mapper 造数据（事件驱动落库是 Task 2 的验收点）。注意 `@MapperScan` 已注册 Mapper，可在测试中 `@Autowired NotificationMapper`。

```java
package com.linklife.notify;

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
import com.linklife.notify.entity.Notification;
import com.linklife.notify.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class NotificationApiTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationMapper notificationMapper;

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

    private long insertNotification(long userId, String type, String title, int isRead) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent("内容");
        n.setIsRead(isRead);
        notificationMapper.insert(n);
        return n.getId();
    }

    private long currentUserId(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(me.getResponse().getContentAsString(),
                "$.data.id").toString());
    }

    @Test
    void listReturnsItemsWithUnreadCountAndCursorPagination() throws Exception {
        String token = login("notify-list-1");
        long userId = currentUserId(token);
        insertNotification(userId, "SHEET_SHARED", "旧通知", 1);
        insertNotification(userId, "ITEM_CLAIMED", "新通知1", 0);
        insertNotification(userId, "ITEM_DONE", "新通知2", 0);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].title").value("新通知2"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.items[2].read").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(2));

        MvcResult page2 = mockMvc.perform(get("/api/notifications?afterId="
                                + (JsonPath.<Integer>read(mockMvc.perform(get("/api/notifications")
                                        .header("Authorization", "Bearer " + token))
                                .andReturn().getResponse().getContentAsString(),
                                "$.data.items[0].id")))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(2,
                ((java.util.List<?>) JsonPath.read(page2.getResponse().getContentAsString(),
                        "$.data.items[*]")).size());
    }

    @Test
    void markReadAndReadAllUpdateIsRead() throws Exception {
        String token = login("notify-read-1");
        long userId = currentUserId(token);
        long id1 = insertNotification(userId, "ITEM_CLAIMED", "通知A", 0);
        long id2 = insertNotification(userId, "ITEM_DONE", "通知B", 0);

        mockMvc.perform(post("/api/notifications/" + id1 + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(post("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        // 重复标记已读不报错（幂等）
        mockMvc.perform(post("/api/notifications/" + id2 + "/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void markReadOthersNotificationRejected() throws Exception {
        String mine = login("notify-read-2");
        String other = login("notify-read-3");
        long otherId = currentUserId(other);
        long id = insertNotification(otherId, "ITEM_CLAIMED", "别人的", 0);

        mockMvc.perform(post("/api/notifications/" + id + "/read")
                        .header("Authorization", "Bearer " + mine))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3006));
    }
}
```

- [ ] **Step 6: 跑测试确认通过**

Run: `cd server && mvn test -Dtest=NotificationApiTest`
Expected: 3 个测试 PASS

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: notification inbox table, service and read APIs"
```

---

## Task 2: 领域事件 + OrderService publish + 异步分发器落库

**Files:**
- Create: `server/src/main/java/com/linklife/notify/event/SheetSharedEvent.java`
- Create: `server/src/main/java/com/linklife/notify/event/ItemClaimedEvent.java`
- Create: `server/src/main/java/com/linklife/notify/event/ItemDoneEvent.java`
- Create: `server/src/main/java/com/linklife/notify/event/SheetCompletedEvent.java`
- Create: `server/src/main/java/com/linklife/notify/NotifyEventDispatcher.java`
- Create: `server/src/main/java/com/linklife/notify/NotifyAsyncConfig.java`
- Create: `server/src/main/java/com/linklife/notify/channel/PersonalChannel.java`
- Create: `server/src/main/java/com/linklife/notify/channel/BroadcastChannel.java`
- Modify: `server/src/main/java/com/linklife/order/OrderService.java`
- Modify: `server/src/main/java/com/linklife/circle/CircleService.java`
- Test: `server/src/test/java/com/linklife/notify/NotificationEventTest.java`

**Interfaces:**
- Consumes: Task 1 的 `NotificationService.persist(...)`、`UserService.getNicknames(Collection<Long>) -> Map<Long,String>`
- Produces:
  - `CircleService.listMemberIds(long circleId) -> List<Long>`（无权限校验，供异步分发器用）
  - 事件 record（构造器参数顺序固定，Task 3/4 单测会用到）：
    - `SheetSharedEvent(long circleId, long sheetId, String title, long creatorId, int itemCount)`
    - `ItemClaimedEvent(long sheetId, String sheetTitle, long itemId, String dishName, long claimantId, long creatorId)`
    - `ItemDoneEvent(long sheetId, String sheetTitle, long itemId, String dishName, long claimantId, long creatorId)`
    - `SheetCompletedEvent(long circleId, long sheetId, String title, long creatorId)`
  - `PersonalChannel { boolean enabled(); void send(Notification notification); }`
  - `BroadcastChannel { boolean enabled(); void broadcast(String summary); }`
  - 文案模板（spec 第 1 节）：SHEET_SHARED=`{发起人} 发起点单「{标题}」`/`共 {N} 道菜，快来认领`；ITEM_CLAIMED=`{认领人} 认领了「{菜名}」`/`点单「{标题}」`；ITEM_DONE=`{认领人} 做完了「{菜名}」`/`点单「{标题}」`；SHEET_COMPLETED=`点单「{标题}」已全部完成`/`🎉 收工开饭`

- [ ] **Step 1: 写事件类与通道接口**

```java
package com.linklife.notify.event;

public record SheetSharedEvent(long circleId, long sheetId, String title,
                               long creatorId, int itemCount) {
}
```

```java
package com.linklife.notify.event;

public record ItemClaimedEvent(long sheetId, String sheetTitle, long itemId,
                               String dishName, long claimantId, long creatorId) {
}
```

```java
package com.linklife.notify.event;

public record ItemDoneEvent(long sheetId, String sheetTitle, long itemId,
                            String dishName, long claimantId, long creatorId) {
}
```

```java
package com.linklife.notify.event;

public record SheetCompletedEvent(long circleId, long sheetId, String title, long creatorId) {
}
```

```java
package com.linklife.notify.channel;

import com.linklife.notify.entity.Notification;

/** 按接收人逐条投递的通道（如微信订阅消息）。 */
public interface PersonalChannel {
    boolean enabled();

    void send(Notification notification);
}
```

```java
package com.linklife.notify.channel;

/** 全局广播通道（如飞书群 Webhook），每个事件发一条摘要。 */
public interface BroadcastChannel {
    boolean enabled();

    void broadcast(String summary);
}
```

- [ ] **Step 2: 写异步配置**

```java
package com.linklife.notify;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class NotifyAsyncConfig {

    @Bean("notifyExecutor")
    public ThreadPoolTaskExecutor notifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("notify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 3: 写 NotifyEventDispatcher**

```java
package com.linklife.notify;

import com.linklife.circle.CircleService;
import com.linklife.notify.channel.BroadcastChannel;
import com.linklife.notify.channel.PersonalChannel;
import com.linklife.notify.entity.Notification;
import com.linklife.notify.event.ItemClaimedEvent;
import com.linklife.notify.event.ItemDoneEvent;
import com.linklife.notify.event.SheetCompletedEvent;
import com.linklife.notify.event.SheetSharedEvent;
import com.linklife.user.UserService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyEventDispatcher {

    private final NotificationService notificationService;
    private final CircleService circleService;
    private final UserService userService;
    private final List<PersonalChannel> personalChannels;
    private final List<BroadcastChannel> broadcastChannels;

    @Async("notifyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSheetShared(SheetSharedEvent e) {
        String title = nickname(e.creatorId()) + " 发起点单「" + e.title() + "」";
        String content = "共 " + e.itemCount() + " 道菜，快来认领";
        dispatchToMembers(e.circleId(), e.creatorId(), "SHEET_SHARED", title, content, e.sheetId());
        broadcast(title + "，" + content);
    }

    @Async("notifyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onItemClaimed(ItemClaimedEvent e) {
        notifyCreatorForItem(e.sheetId(), e.sheetTitle(), e.itemId(), e.dishName(),
                e.claimantId(), e.creatorId(), "ITEM_CLAIMED", "认领了");
    }

    @Async("notifyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onItemDone(ItemDoneEvent e) {
        notifyCreatorForItem(e.sheetId(), e.sheetTitle(), e.itemId(), e.dishName(),
                e.claimantId(), e.creatorId(), "ITEM_DONE", "做完了");
    }

    @Async("notifyExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSheetCompleted(SheetCompletedEvent e) {
        String title = "点单「" + e.title() + "」已全部完成";
        String content = "🎉 收工开饭";
        dispatchToMembers(e.circleId(), e.creatorId(), "SHEET_COMPLETED", title, content, e.sheetId());
        broadcast(title + "，" + content);
    }

    private void dispatchToMembers(long circleId, long actorId, String type,
                                   String title, String content, long sheetId) {
        List<Long> recipients = circleService.listMemberIds(circleId).stream()
                .filter(id -> id != actorId).toList();
        List<Notification> notifications = new ArrayList<>();
        for (Long userId : recipients) {
            notifications.add(notificationService.persist(userId, type, title, content, sheetId, circleId));
        }
        dispatchPersonal(notifications);
    }

    private void notifyCreatorForItem(long sheetId, String sheetTitle, long itemId, String dishName,
                                      long claimantId, long creatorId, String type, String verb) {
        if (claimantId == creatorId) {
            return;
        }
        String title = nickname(claimantId) + " " + verb + "「" + dishName + "」";
        String content = "点单「" + sheetTitle + "」";
        Notification n = notificationService.persist(creatorId, type, title, content, sheetId, null);
        dispatchPersonal(List.of(n));
        broadcast(title + "，" + content);
    }

    private void dispatchPersonal(List<Notification> notifications) {
        for (PersonalChannel channel : personalChannels) {
            if (!channel.enabled()) {
                continue;
            }
            for (Notification n : notifications) {
                try {
                    channel.send(n);
                } catch (Exception ex) {
                    log.warn("notify channel {} send failed: {}",
                            channel.getClass().getSimpleName(), ex.getMessage());
                }
            }
        }
    }

    private void broadcast(String summary) {
        for (BroadcastChannel channel : broadcastChannels) {
            if (!channel.enabled()) {
                continue;
            }
            try {
                channel.broadcast(summary);
            } catch (Exception ex) {
                log.warn("notify channel {} broadcast failed: {}",
                        channel.getClass().getSimpleName(), ex.getMessage());
            }
        }
    }

    private String nickname(long userId) {
        return userService.getNicknames(List.of(userId)).getOrDefault(userId, "用户" + userId);
    }
}
```

注意：测试环境无通道实现时 `personalChannels`/`broadcastChannels` 为空 List，Spring 对 `List<T>` 注入缺失时可能报错——构造器注入 `List<T>` 在 Spring 中无候选 Bean 时注入空 List（`@RequiredArgsConstructor` 场景成立），无需额外处理；若启动报 `NoSuchBeanDefinitionException`，给两个 List 参数加 `@Autowired(required = false)` 并改手写构造器。

- [ ] **Step 4: CircleService 加 listMemberIds**

在 `CircleService.requireMembership` 方法后追加：

```java
    public List<Long> listMemberIds(long circleId) {
        return circleMemberMapper.selectList(
                        new LambdaQueryWrapper<CircleMember>().eq(CircleMember::getCircleId, circleId))
                .stream().map(CircleMember::getUserId).toList();
    }
```

- [ ] **Step 5: OrderService publish 事件**

修改 `order/OrderService.java`：
1. 注入 publisher（`@RequiredArgsConstructor` 自动生成构造器），加字段与 import：

```java
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
```

2. `createSheet` 末尾（`return toDetailVO(sheet);` 之前）：

```java
        eventPublisher.publishEvent(new com.linklife.notify.event.SheetSharedEvent(
                circleId, sheet.getId(), title, userId, items.size()));
```

3. `claim` 末尾（`return toItemVO(item);` 之前）：

```java
        eventPublisher.publishEvent(new com.linklife.notify.event.ItemClaimedEvent(
                sheet.getId(), sheet.getTitle(), item.getId(), item.getDishName(),
                userId, sheet.getCreatorId()));
```

4. `updateItemStatus` 在 `item.setItemStatus(target); orderItemMapper.updateById(item);` 之后、return 之前：

```java
        if (ITEM_DONE.equals(target)) {
            eventPublisher.publishEvent(new com.linklife.notify.event.ItemDoneEvent(
                    sheet.getId(), sheet.getTitle(), item.getId(), item.getDishName(),
                    userId, sheet.getCreatorId()));
        }
```

5. `completeSheet` 在 `orderSheetMapper.updateById(sheet);` 之后、return 之前：

```java
        eventPublisher.publishEvent(new com.linklife.notify.event.SheetCompletedEvent(
                sheet.getCircleId(), sheet.getId(), sheet.getTitle(), userId));
```

6. 顶部 import 改为正式 import（不用全限定名）：`import com.linklife.notify.event.ItemClaimedEvent;` 等 4 个 + `import org.springframework.context.ApplicationEventPublisher;`，字段写 `private final ApplicationEventPublisher eventPublisher;`。

- [ ] **Step 6: 写失败的集成测试**

```java
package com.linklife.notify;

import static org.awaitility.Awaitility.await;
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
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class NotificationEventTest extends IntegrationTestBase {

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

    private long currentUserId(String token) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(me.getResponse().getContentAsString(),
                "$.data.id").toString());
    }

    private long createCircle(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/circles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"通知圈\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id").toString());
    }

    private void join(String memberToken, String inviteCode) throws Exception {
        mockMvc.perform(post("/api/circles/join")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isOk());
    }

    private String inviteCode(String ownerToken, long circleId) throws Exception {
        MvcResult list = mockMvc.perform(get("/api/circles")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(list.getResponse().getContentAsString(),
                "$.data[0].inviteCode");
    }

    private long createSheet(String token, long circleId, String title) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/order/sheets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"circleId\":" + circleId + ",\"title\":\"" + title
                                + "\",\"items\":[{\"dishName\":\"番茄炒蛋\",\"note\":\"少油\"}]}"))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id").toString());
    }

    private long firstItemId(String token, long sheetId) throws Exception {
        MvcResult detail = mockMvc.perform(get("/api/order/sheets/" + sheetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(
                detail.getResponse().getContentAsString(), "$.data.items[0].id").toString());
    }

    /** 异步分发落库是 AFTER_COMMIT + @Async，轮询 unread-count 直到满足期望。 */
    private void awaitUnread(String token, long expected) {
        await().atMost(5, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(100)).until(() -> {
            try {
                MvcResult r = mockMvc.perform(get("/api/notifications/unread-count")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();
                return Long.parseLong(JsonPath.read(
                        r.getResponse().getContentAsString(), "$.data.unreadCount").toString())
                        == expected;
            } catch (AssertionError e) {
                return false;
            }
        });
    }

    @Test
    void sheetSharedNotifiesOtherMembersExceptCreator() throws Exception {
        String alice = login("evt-alice-1");
        String bob = login("evt-bob-1");
        long circleId = createCircle(alice);
        join(bob, inviteCode(alice, circleId));
        // 设置昵称，验证文案渲染
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/me")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"小爱\"}"))
                .andExpect(status().isOk());

        createSheet(alice, circleId, "周五晚餐");

        awaitUnread(bob, 1);
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.data.items[0].type").value("SHEET_SHARED"))
                .andExpect(jsonPath("$.data.items[0].title")
                        .value(org.hamcrest.Matchers.containsString("小爱 发起点单「周五晚餐」")));
        // 发起人自己不收
        awaitUnread(alice, 0);
    }

    @Test
    void claimAndDoneNotifyCreatorAndCompleteNotifiesMembers() throws Exception {
        String alice = login("evt-alice-2");
        String bob = login("evt-bob-2");
        long circleId = createCircle(alice);
        join(bob, inviteCode(alice, circleId));
        long sheetId = createSheet(alice, circleId, "状态机通知");
        long itemId = firstItemId(alice, sheetId);

        mockMvc.perform(post("/api/order/items/" + itemId + "/claim")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk());
        awaitUnread(alice, 1);

        mockMvc.perform(post("/api/order/items/" + itemId + "/status")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemStatus\":\"DONE\"}"))
                .andExpect(status().isOk());
        awaitUnread(alice, 2);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data.items[0].type").value("ITEM_DONE"))
                .andExpect(jsonPath("$.data.items[1].type").value("ITEM_CLAIMED"));

        mockMvc.perform(post("/api/order/sheets/" + sheetId + "/complete")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk());
        awaitUnread(bob, 2); // SHEET_COMPLETED + 之前的 SHEET_SHARED
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.data.items[0].type").value("SHEET_COMPLETED"));
    }

    @Test
    void creatorClaimingOwnItemGeneratesNoNotification() throws Exception {
        String alice = login("evt-alice-3");
        long circleId = createCircle(alice);
        long sheetId = createSheet(alice, circleId, "自认领");
        long itemId = firstItemId(alice, sheetId);

        mockMvc.perform(post("/api/order/items/" + itemId + "/claim")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk());
        awaitUnread(alice, 0);
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }
}
```

awaitility 依赖检查：`server/pom.xml` 无 awaitility，需要在 `<dependencies>` 加入（spring-boot-starter-test 自带版本管理）：

```xml
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 7: 跑测试确认通过**

Run: `cd server && mvn test -Dtest=NotificationEventTest`
Expected: 3 个测试 PASS

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat: domain events with async notification dispatcher"
```

---

## Task 3: FeishuChannel（全局群 Webhook，配置开关）

**Files:**
- Create: `server/src/main/java/com/linklife/notify/channel/FeishuProperties.java`
- Create: `server/src/main/java/com/linklife/notify/channel/FeishuChannel.java`
- Modify: `server/src/main/resources/application.yml`
- Test: `server/src/test/java/com/linklife/notify/channel/FeishuChannelTest.java`

**Interfaces:**
- Consumes: Task 2 的 `BroadcastChannel { boolean enabled(); void broadcast(String summary); }`
- Produces: 配置项 `link.notify.feishu.enabled` / `link.notify.feishu.webhook-url`（环境变量 `FEISHU_NOTIFY_ENABLED` / `FEISHU_WEBHOOK_URL`，均默认空→通道禁用）

- [ ] **Step 1: 写配置属性类与 yml**

```java
package com.linklife.notify.channel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "link.notify.feishu")
public class FeishuProperties {
    private boolean enabled;
    private String webhookUrl;
}
```

`application.yml` 在 `link.wx:` 段后追加：

```yaml
  notify:
    feishu:
      enabled: ${FEISHU_NOTIFY_ENABLED:false}
      webhook-url: ${FEISHU_WEBHOOK_URL:}
    wx-subscribe:
      enabled: ${WX_SUBSCRIBE_ENABLED:false}
      template-id: ${WX_SUBSCRIBE_TEMPLATE_ID:}
      page: pages/sheet-detail/sheet-detail
```

（`wx-subscribe` 段 Task 4 使用，一次写好。）

- [ ] **Step 2: 写失败的单元测试**

```java
package com.linklife.notify.channel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FeishuChannelTest {

    private FeishuProperties enabledProps() {
        FeishuProperties props = new FeishuProperties();
        props.setEnabled(true);
        props.setWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/test-hook");
        return props;
    }

    @Test
    void broadcastPostsTextMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://open.feishu.cn/open-apis/bot/v2/hook/test-hook"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.msg_type").value("text"))
                .andExpect(jsonPath("$.content.text")
                        .value("[Link-Life] 小爱 发起点单「周五晚餐」，共 3 道菜，快来认领"))
                .andRespond(withSuccess("{\"code\":0}", MediaType.APPLICATION_JSON));

        FeishuChannel channel = new FeishuChannel(enabledProps(), builder);
        channel.broadcast("小爱 发起点单「周五晚餐」，共 3 道菜，快来认领");
        server.verify();
    }

    @Test
    void nonZeroFeishuCodeDoesNotThrow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://open.feishu.cn/open-apis/bot/v2/hook/test-hook"))
                .andRespond(withSuccess("{\"code\":19021,\"msg\":\"invalid\"}",
                        MediaType.APPLICATION_JSON));

        FeishuChannel channel = new FeishuChannel(enabledProps(), builder);
        assertDoesNotThrow(() -> channel.broadcast("任意"));
        server.verify();
    }

    @Test
    void disabledWhenConfigMissing() {
        FeishuProperties props = new FeishuProperties();
        assertFalse(new FeishuChannel(props, RestClient.builder()).enabled());

        props.setEnabled(true);
        props.setWebhookUrl("");
        assertFalse(new FeishuChannel(props, RestClient.builder()).enabled());

        props.setWebhookUrl("https://example.com/hook");
        assertTrue(new FeishuChannel(props, RestClient.builder()).enabled());
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `cd server && mvn test -Dtest=FeishuChannelTest`
Expected: 编译失败（FeishuChannel 不存在）

- [ ] **Step 4: 实现 FeishuChannel**

```java
package com.linklife.notify.channel;

import java.net.URI;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class FeishuChannel implements BroadcastChannel {

    private final FeishuProperties props;
    private final RestClient restClient;

    public FeishuChannel(FeishuProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder.build();
    }

    @Override
    public boolean enabled() {
        return props.isEnabled() && props.getWebhookUrl() != null && !props.getWebhookUrl().isBlank();
    }

    @Override
    public void broadcast(String summary) {
        Map<String, Object> body = Map.of(
                "msg_type", "text",
                "content", Map.of("text", "[Link-Life] " + summary));
        String resp = restClient.post()
                .uri(URI.create(props.getWebhookUrl()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        if (resp != null && !resp.contains("\"code\":0") && !resp.contains("\"StatusCode\":0")) {
            log.warn("feishu webhook non-success response: {}", resp);
        }
    }
}
```

- [ ] **Step 5: 跑测试确认通过 + 全量回归**

Run: `cd server && mvn clean test`
Expected: 全部 PASS（集成测试环境 `FEISHU_NOTIFY_ENABLED` 未设置 → 通道 disabled，不触网）

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: feishu webhook broadcast channel with config switch"
```

---

## Task 4: WxMpAccessTokenService + WxSubscribeChannel

**Files:**
- Create: `server/src/main/java/com/linklife/notify/channel/WxSubscribeProperties.java`
- Create: `server/src/main/java/com/linklife/notify/wechat/WxMpAccessTokenService.java`
- Create: `server/src/main/java/com/linklife/notify/channel/WxSubscribeChannel.java`
- Test: `server/src/test/java/com/linklife/notify/wechat/WxMpAccessTokenServiceTest.java`
- Test: `server/src/test/java/com/linklife/notify/channel/WxSubscribeChannelTest.java`

**Interfaces:**
- Consumes: `WeChatProperties`（`auth/wechat`，提供 appid/secret/apiBase）、`UserService.getUserById(long) -> User`（取 openid）、Task 2 的 `PersonalChannel`、Task 1 的 `Notification`
- Produces:
  - `WxMpAccessTokenService.getToken() -> String`（Caffeine 缓存，`expires_in - 300s` 过期，并发原子加载；失败抛 IllegalStateException 由调用方 catch）
  - `WxMpAccessTokenService.invalidate()`（40001 时调用）
  - `WxSubscribeChannel implements PersonalChannel`：POST `{apiBase}/cgi-bin/message/subscribe/send?access_token=`，body `{touser, template_id, page, data:{thing1:{value:title}, thing2:{value:content}}}`（thing 值截断 20 字）；errcode 43101 记 info，其他非 0 记 warn，均不抛

- [ ] **Step 1: 写配置属性类**

```java
package com.linklife.notify.channel;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "link.notify.wx-subscribe")
public class WxSubscribeProperties {
    private boolean enabled;
    private String templateId;
    private String page = "pages/sheet-detail/sheet-detail";
}
```

- [ ] **Step 2: 写失败的 WxMpAccessTokenServiceTest**

```java
package com.linklife.notify.wechat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.linklife.auth.wechat.WeChatProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class WxMpAccessTokenServiceTest {

    private WeChatProperties props() {
        WeChatProperties props = new WeChatProperties();
        props.setAppid("wx-app");
        props.setSecret("wx-secret");
        props.setApiBase("https://api.weixin.qq.com");
        return props;
    }

    private URI tokenUri() {
        return UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com")
                .path("/cgi-bin/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", "wx-app")
                .queryParam("secret", "wx-secret")
                .build().toUri();
    }

    @Test
    void cachesTokenUntilExpiry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 只允许一次请求：第二次 getToken() 必须命中缓存
        server.expect(requestTo(tokenUri()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"access_token\":\"TOKEN-1\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));

        WxMpAccessTokenService service = new WxMpAccessTokenService(props(), builder);
        assertEquals("TOKEN-1", service.getToken());
        assertEquals("TOKEN-1", service.getToken());
        server.verify();
    }

    @Test
    void invalidateForcesRefetch() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(tokenUri()))
                .andRespond(withSuccess(
                        "{\"access_token\":\"TOKEN-1\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(tokenUri()))
                .andRespond(withSuccess(
                        "{\"access_token\":\"TOKEN-2\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));

        WxMpAccessTokenService service = new WxMpAccessTokenService(props(), builder);
        assertEquals("TOKEN-1", service.getToken());
        service.invalidate();
        assertEquals("TOKEN-2", service.getToken());
        server.verify();
    }

    @Test
    void errcodeResponseThrows() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(tokenUri()))
                .andRespond(withSuccess(
                        "{\"errcode\":40013,\"errmsg\":\"invalid appid\"}",
                        MediaType.APPLICATION_JSON));

        WxMpAccessTokenService service = new WxMpAccessTokenService(props(), builder);
        assertThrows(IllegalStateException.class, service::getToken);
        server.verify();
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `cd server && mvn test -Dtest=WxMpAccessTokenServiceTest`
Expected: 编译失败

- [ ] **Step 4: 实现 WxMpAccessTokenService**

```java
package com.linklife.notify.wechat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.linklife.auth.wechat.WeChatProperties;
import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class WxMpAccessTokenService {

    private final WeChatProperties props;
    private final RestClient restClient;
    private final Cache<String, CachedToken> tokens = Caffeine.newBuilder()
            .expireAfter(new Expiry<String, CachedToken>() {
                private long nanos(CachedToken t) {
                    return Duration.ofSeconds(Math.max(t.expiresIn() - 300, 60)).toNanos();
                }

                @Override
                public long expireAfterCreate(String key, CachedToken t, long now) {
                    return nanos(t);
                }

                @Override
                public long expireAfterUpdate(String key, CachedToken t, long now, long current) {
                    return nanos(t);
                }

                @Override
                public long expireAfterRead(String key, CachedToken t, long now, long current) {
                    return Long.MAX_VALUE;
                }
            })
            .build();

    public WxMpAccessTokenService(WeChatProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder.build();
    }

    public String getToken() {
        CachedToken cached = tokens.get("global", k -> fetch());
        if (cached == null) {
            throw new IllegalStateException("获取微信 access_token 失败");
        }
        return cached.accessToken();
    }

    public void invalidate() {
        tokens.invalidate("global");
    }

    private CachedToken fetch() {
        URI uri = UriComponentsBuilder.fromHttpUrl(props.getApiBase())
                .path("/cgi-bin/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", props.getAppid())
                .queryParam("secret", props.getSecret())
                .build().toUri();
        TokenResponse resp = restClient.get().uri(uri).retrieve().body(TokenResponse.class);
        if (resp == null || resp.accessToken() == null || resp.accessToken().isBlank()) {
            log.warn("wx access_token fetch failed: {}", resp);
            return null;
        }
        return new CachedToken(resp.accessToken(), resp.expiresIn() == null ? 7200 : resp.expiresIn());
    }

    private record CachedToken(String accessToken, int expiresIn) {
    }

    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Integer expiresIn,
            Integer errcode, String errmsg) {
    }
}
```

- [ ] **Step 5: 写失败的 WxSubscribeChannelTest**

需要构造 `Notification`（Task 1 实体，直接 new + setter）与 `User`（`com.linklife.user.entity.User`）。`UserService` 用 Mockito mock（`mock(UserService.class)`，`when(getUserById(...))` 返回带 openid 的 User）。

```java
package com.linklife.notify.channel;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.linklife.notify.entity.Notification;
import com.linklife.notify.wechat.WxMpAccessTokenService;
import com.linklife.user.UserService;
import com.linklife.user.entity.User;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class WxSubscribeChannelTest {

    private WxSubscribeProperties enabledProps() {
        WxSubscribeProperties props = new WxSubscribeProperties();
        props.setEnabled(true);
        props.setTemplateId("TPL-ID");
        return props;
    }

    private Notification notification() {
        Notification n = new Notification();
        n.setUserId(7L);
        n.setType("ITEM_CLAIMED");
        n.setTitle("小博 认领了「番茄炒蛋」");
        n.setContent("点单「周五晚餐」");
        n.setSheetId(42L);
        return n;
    }

    private UserService userServiceWithOpenid() {
        UserService userService = mock(UserService.class);
        User user = new User();
        user.setId(7L);
        user.setOpenid("o-abc");
        when(userService.getUserById(7L)).thenReturn(user);
        return userService;
    }

    private URI sendUri() {
        return UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com")
                .path("/cgi-bin/message/subscribe/send")
                .queryParam("access_token", "TOKEN-1")
                .build().toUri();
    }

    @Test
    void sendsSubscribeMessageWithTemplateData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(sendUri()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.touser").value("o-abc"))
                .andExpect(jsonPath("$.template_id").value("TPL-ID"))
                .andExpect(jsonPath("$.page").value("pages/sheet-detail/sheet-detail?id=42"))
                .andExpect(jsonPath("$.data.thing1.value").value("小博 认领了「番茄炒蛋」"))
                .andExpect(jsonPath("$.data.thing2.value").value("点单「周五晚餐」"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}",
                        MediaType.APPLICATION_JSON));

        WxSubscribeChannel channel = new WxSubscribeChannel(enabledProps(),
                tokenService(), userServiceWithOpenid(), wxProps(), builder);
        channel.send(notification());
        server.verify();
    }

    @Test
    void userNotSubscribedDoesNotThrow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(sendUri()))
                .andRespond(withSuccess("{\"errcode\":43101,\"errmsg\":\"user refused\"}",
                        MediaType.APPLICATION_JSON));

        WxSubscribeChannel channel = new WxSubscribeChannel(enabledProps(),
                tokenService(), userServiceWithOpenid(), wxProps(), builder);
        assertDoesNotThrow(() -> channel.send(notification()));
        server.verify();
    }

    @Test
    void longValuesTruncatedTo20Chars() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(sendUri()))
                .andExpect(jsonPath("$.data.thing1.value").value("01234567890123456789"))
                .andRespond(withSuccess("{\"errcode\":0}", MediaType.APPLICATION_JSON));

        Notification n = notification();
        n.setTitle("01234567890123456789超出的部分被截断");
        WxSubscribeChannel channel = new WxSubscribeChannel(enabledProps(),
                tokenService(), userServiceWithOpenid(), wxProps(), builder);
        channel.send(n);
        server.verify();
    }

    @Test
    void disabledWhenConfigMissing() {
        WxSubscribeProperties props = new WxSubscribeProperties();
        assertFalse(new WxSubscribeChannel(props, tokenService(),
                userServiceWithOpenid(), wxProps(), RestClient.builder()).enabled());
        props.setEnabled(true);
        props.setTemplateId("");
        assertFalse(new WxSubscribeChannel(props, tokenService(),
                userServiceWithOpenid(), wxProps(), RestClient.builder()).enabled());
        props.setTemplateId("TPL");
        assertTrue(new WxSubscribeChannel(props, tokenService(),
                userServiceWithOpenid(), wxProps(), RestClient.builder()).enabled());
    }

    private WxMpAccessTokenService tokenService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.weixin.qq.com/cgi-bin/token?"
                        + "grant_type=client_credential&appid=wx-app&secret=wx-secret"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"TOKEN-1\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
        WeChatProperties props = new WeChatProperties();
        props.setAppid("wx-app");
        props.setSecret("wx-secret");
        props.setApiBase("https://api.weixin.qq.com");
        WxMpAccessTokenService service = new WxMpAccessTokenService(props, builder);
        service.getToken();
        return service;
    }

    private WeChatProperties wxProps() {
        WeChatProperties props = new WeChatProperties();
        props.setApiBase("https://api.weixin.qq.com");
        return props;
    }
}
```

注意：tokenService() 里的 URL 断言依赖参数顺序，若 MockRestServiceServer 匹配失败，改用 `requestTo(startsWith(...))` 放宽。

- [ ] **Step 6: 跑测试确认失败**

Run: `cd server && mvn test -Dtest=WxSubscribeChannelTest`
Expected: 编译失败

- [ ] **Step 7: 实现 WxSubscribeChannel**

```java
package com.linklife.notify.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.auth.wechat.WeChatProperties;
import com.linklife.notify.entity.Notification;
import com.linklife.notify.wechat.WxMpAccessTokenService;
import com.linklife.user.UserService;
import com.linklife.user.entity.User;
import java.net.URI;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class WxSubscribeChannel implements PersonalChannel {

    private static final int THING_MAX_LENGTH = 20;
    private static final int ERR_OK = 0;
    private static final int ERR_TOKEN_INVALID = 40001;
    private static final int ERR_USER_REFUSED = 43101;

    private final WxSubscribeProperties props;
    private final WxMpAccessTokenService tokenService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final String apiBase;
    private final RestClient restClient;

    public WxSubscribeChannel(WxSubscribeProperties props, WxMpAccessTokenService tokenService,
                              UserService userService, WeChatProperties weChatProps,
                              RestClient.Builder builder) {
        this.props = props;
        this.tokenService = tokenService;
        this.userService = userService;
        this.objectMapper = new ObjectMapper();
        this.apiBase = weChatProps.getApiBase();
        this.restClient = builder.build();
    }

    @Override
    public boolean enabled() {
        return props.isEnabled() && props.getTemplateId() != null && !props.getTemplateId().isBlank();
    }

    @Override
    public void send(Notification notification) {
        User user = userService.getUserById(notification.getUserId());
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()) {
            return;
        }
        sendWithRetry(user.getOpenid(), notification, true);
    }

    private void sendWithRetry(String openid, Notification notification, boolean allowRetry) {
        String page = notification.getSheetId() == null ? props.getPage()
                : props.getPage() + "?id=" + notification.getSheetId();
        Map<String, Object> body = Map.of(
                "touser", openid,
                "template_id", props.getTemplateId(),
                "page", page,
                "data", Map.of(
                        "thing1", Map.of("value", truncate(notification.getTitle())),
                        "thing2", Map.of("value", truncate(notification.getContent()))));
        URI uri = UriComponentsBuilder.fromHttpUrl(apiBase)
                .path("/cgi-bin/message/subscribe/send")
                .queryParam("access_token", tokenService.getToken())
                .build().toUri();
        String resp = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        WxSendResponse parsed = parse(resp);
        int errcode = parsed == null || parsed.errcode() == null ? -1 : parsed.errcode();
        if (errcode == ERR_OK) {
            log.debug("wx subscribe send ok: userId={}", notification.getUserId());
            return;
        }
        if (errcode == ERR_USER_REFUSED) {
            log.info("wx subscribe send skipped (user not subscribed): userId={}",
                    notification.getUserId());
            return;
        }
        if (errcode == ERR_TOKEN_INVALID && allowRetry) {
            log.info("wx access_token invalid, refresh and retry once");
            tokenService.invalidate();
            sendWithRetry(openid, notification, false);
            return;
        }
        log.warn("wx subscribe send failed: userId={}, resp={}", notification.getUserId(), resp);
    }

    private WxSendResponse parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, WxSendResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= THING_MAX_LENGTH ? value : value.substring(0, THING_MAX_LENGTH);
    }

    private record WxSendResponse(Integer errcode, String errmsg) {
    }
}
```

- [ ] **Step 8: 跑测试确认通过 + 全量回归**

Run: `cd server && mvn clean test`
Expected: 全部 PASS

- [ ] **Step 9: Commit**

```bash
git add -A && git commit -m "feat: wx subscribe message channel with cached access token"
```

---

## Task 5: Web 端站内信页 + 全局轮询红点

**Files:**
- Create: `web/src/api/notifications.js`
- Create: `web/src/views/Notifications.vue`
- Modify: `web/src/router.js`（加 `/notifications` 路由）
- Modify: `web/src/App.vue`（导航"通知"入口 + 30s 轮询红点）

**Interfaces:**
- Consumes: Task 1 的 4 个 API；`web/src/api/request.js` 的 `request()`
- Produces: `web/src/api/notifications.js` 导出 `listNotifications(afterId, size)`、`unreadCount()`、`markRead(id)`、`markAllRead()`；路由 `/notifications`（requiresAuth）

- [ ] **Step 1: 写 API 封装**

```javascript
import { request } from './request'

export function listNotifications(afterId, size = 20) {
  const params = new URLSearchParams()
  if (afterId) params.set('afterId', String(afterId))
  params.set('size', String(size))
  return request('/api/notifications?' + params.toString())
}

export function fetchUnreadCount() {
  return request('/api/notifications/unread-count')
}

export function markRead(id) {
  return request(`/api/notifications/${id}/read`, { method: 'POST' })
}

export function markAllRead() {
  return request('/api/notifications/read-all', { method: 'POST' })
}
```

- [ ] **Step 2: 写 Notifications.vue**

```vue
<template>
  <div>
    <div class="row" style="margin-bottom: 12px">
      <h3 style="margin: 0">我的通知</h3>
      <button class="btn btn-ghost btn-small" style="margin-left: auto" @click="onReadAll"
              :disabled="allDone">全部已读</button>
    </div>
    <p v-if="items.length === 0" class="muted">暂无通知</p>
    <div v-for="n in items" :key="n.id" class="card clickable" @click="open(n)">
      <div class="row">
        <span class="dot" v-if="!n.read"></span>
        <span :class="{ unread: !n.read }">{{ n.title }}</span>
      </div>
      <div class="muted">{{ n.content }}</div>
    </div>
    <button v-if="items.length >= 20" class="btn btn-ghost" style="width: 100%"
            @click="loadMore">加载更多</button>
  </div>
</template>

<script>
import { listNotifications, markAllRead, markRead } from '../api/notifications'

export default {
  data() {
    return { items: [], allDone: false }
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      const data = await listNotifications(null)
      this.items = data.items
      this.allDone = data.unreadCount === 0
    },
    async loadMore() {
      const last = this.items.length ? this.items[this.items.length - 1].id : null
      const data = await listNotifications(last)
      this.items = this.items.concat(data.items)
    },
    async open(n) {
      if (!n.read) {
        await markRead(n.id)
        n.read = true
      }
      if (n.sheetId) this.$router.push('/sheets/' + n.sheetId)
    },
    async onReadAll() {
      await markAllRead()
      this.items.forEach((n) => { n.read = true })
      this.allDone = true
    },
  },
}
</script>

<style scoped>
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #4f7cff;
  display: inline-block;
  margin-right: 6px;
  flex-shrink: 0;
}
.unread {
  font-weight: 600;
}
</style>
```

- [ ] **Step 3: 注册路由**

`web/src/router.js`：import `Notifications from './views/Notifications.vue'`，routes 数组在 `/sheets/:id` 后加：

```javascript
  { path: '/notifications', component: Notifications, meta: { requiresAuth: true } },
```

- [ ] **Step 4: App.vue 加通知入口与轮询**

`web/src/App.vue` 修改：

1. template topbar 中，`<span class="nickname">` 之前加：

```html
      <router-link v-if="user" to="/notifications" class="nav-notif">
        通知<span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
      </router-link>
```

2. script setup 区，顶部 import 补一行：

```javascript
import { fetchUnreadCount } from './api/notifications'
```

setup 内加状态与轮询逻辑：

```javascript
    const unread = ref(0)
    let pollTimer = null

    async function refreshUnread() {
      if (document.hidden || !localStorage.getItem('accessToken')) return
      try {
        const data = await fetchUnreadCount()
        unread.value = data.unreadCount
      } catch (err) {
        // 401 等错误静默，请求封装已处理刷新/跳登录
      }
    }

    function startPolling() {
      if (pollTimer) return
      refreshUnread()
      pollTimer = setInterval(refreshUnread, 30000)
    }

    function stopPolling() {
      if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
      unread.value = 0
    }
```

在现有 `watch(() => route.path, ...)` 的回调里维护启停：

```javascript
      if (path !== '/login' && localStorage.getItem('accessToken')) {
        if (!user.value) loadUser()
        startPolling()
      } else {
        stopPolling()
      }
```

`logout()` 里加 `stopPolling()`。return 对象加 `unread`。

3. style 区加：

```css
.nav-notif {
  margin-left: auto;
  color: #333;
  text-decoration: none;
  font-size: 14px;
  position: relative;
}
.nav-notif .badge {
  display: inline-block;
  margin-left: 4px;
  padding: 0 6px;
  border-radius: 10px;
  background: #ff4d4f;
  color: #fff;
  font-size: 12px;
  line-height: 18px;
}
.nickname {
  margin-left: 0;
}
```

（原 `.nickname { margin-left: auto; }` 移除 auto margin，由 .nav-notif 承接；未登录时 nickname 仍需右对齐——用 `.topbar span:last-child { margin-left: auto; }` 或保留 .nickname 的 auto 并把 .nav-notif 放 nickname 之前自测调整，实现时以布局正常为准。）

- [ ] **Step 5: 构建验证**

Run: `cd web && npm run build`
Expected: 构建成功，产物输出到 `web-dist/`

- [ ] **Step 6: 手工验证（后端本地起 compose 或 spring-boot:run）**

`npm run dev` 或构建后经 nginx：登录 → 无通知时"通知"无红点；用另一账号触发事件（建单/认领）→ 30s 内红点出现 → 通知页可见未读加粗 → 点击跳清单并消红点 → "全部已读"后红点消失。

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: web notification inbox page with unread badge polling"
```

---

## Task 6: 小程序通知页 + profile 入口

**Files:**
- Create: `miniapp/pages/notifications/notifications.js`
- Create: `miniapp/pages/notifications/notifications.wxml`
- Create: `miniapp/pages/notifications/notifications.wxss`
- Create: `miniapp/pages/notifications/notifications.json`
- Modify: `miniapp/app.json`（注册页面）
- Modify: `miniapp/pages/profile/profile.js`（onShow 查未读数）
- Modify: `miniapp/pages/profile/profile.wxml`（"我的通知"入口 + 角标）

**Interfaces:**
- Consumes: Task 1 的 4 个 API、`utils/request.js` 的 `request()`
- Produces: 页面路径 `/pages/notifications/notifications`（Task 3 配置的 wx-subscribe `page` 前缀即清单详情页，与通知页跳转无关）

- [ ] **Step 1: 注册页面**

`miniapp/app.json` pages 数组追加 `"pages/notifications/notifications"`（放在 `pages/profile/profile` 之前）。

- [ ] **Step 2: 写通知页**

`notifications.js`：

```javascript
const { request } = require('../../utils/request');

Page({
  data: {
    items: [],
    allDone: true,
  },

  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    return request('/api/notifications?size=20')
      .then((data) => {
        this.setData({ items: data.items, allDone: data.unreadCount === 0 });
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '加载通知失败', icon: 'none' });
      });
  },

  openItem(e) {
    const { id, sheetid, read } = e.currentTarget.dataset;
    if (!read) {
      request('/api/notifications/' + id + '/read', { method: 'POST' }).catch(() => {});
    }
    if (sheetid) {
      wx.navigateTo({ url: '/pages/sheet-detail/sheet-detail?id=' + sheetid });
    }
  },

  readAll() {
    request('/api/notifications/read-all', { method: 'POST' })
      .then(() => this.load())
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' });
      });
  },
});
```

`notifications.wxml`：

```xml
<view class="page">
  <view class="head">
    <text class="title">我的通知</text>
    <text class="read-all" bindtap="readAll">全部已读</text>
  </view>
  <view wx:if="{{items.length === 0}}" class="empty">暂无通知</view>
  <view wx:for="{{items}}" wx:key="id" class="item" data-id="{{item.id}}"
        data-sheetid="{{item.sheetId}}" data-read="{{item.read}}" bindtap="openItem">
    <view class="row">
      <view wx:if="{{!item.read}}" class="dot"></view>
      <text class="{{item.read ? '' : 'unread'}}">{{item.title}}</text>
    </view>
    <view class="content">{{item.content}}</view>
  </view>
</view>
```

`notifications.wxss`：

```css
.page { padding: 16rpx; }
.head { display: flex; align-items: center; padding: 16rpx 8rpx; }
.title { font-size: 34rpx; font-weight: 600; flex: 1; }
.read-all { color: #4f7cff; font-size: 28rpx; }
.empty { color: #999; text-align: center; padding: 80rpx 0; }
.item { background: #fff; border-radius: 12rpx; padding: 20rpx; margin-bottom: 16rpx; }
.row { display: flex; align-items: center; }
.dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: #4f7cff; margin-right: 10rpx; }
.unread { font-weight: 600; }
.content { color: #999; font-size: 26rpx; margin-top: 8rpx; margin-left: 24rpx; }
```

`notifications.json`：

```json
{
  "navigationBarTitleText": "我的通知",
  "enablePullDownRefresh": true
}
```

注意：`data-sheetid` 取 `item.sheetId` 可能是 null，openItem 里 `if (sheetid)` 已兜底；wxml 中 `wx:if`/`wx:for` 按现有页面惯例包裹（P1 教训：列表加 `wx:for` 的元素若与 `wx:else` 相邻需用 `<block>` 包裹）。

- [ ] **Step 3: profile 页加入口**

`profile.js` 的 `data` 加 `unread: 0`，`onShow` 里在现有 `request('/api/me')` 之前加：

```javascript
    request('/api/notifications/unread-count')
      .then((d) => this.setData({ unread: d.unreadCount }))
      .catch(() => {});
```

`profile.wxml` 在合适位置（参考现有布局，如圈子入口 `goCircle` 行附近）加：

```xml
<view class="menu-row" bindtap="goNotifications">
  <text>我的通知</text>
  <view wx:if="{{unread > 0}}" class="badge">{{unread > 99 ? '99+' : unread}}</view>
</view>
```

`profile.js` 加方法：

```javascript
  goNotifications() {
    wx.navigateTo({ url: '/pages/notifications/notifications' });
  },
```

`profile.wxss` 参照现有 menu 行样式补 `.menu-row`（若无现成样式）与 `.badge`：

```css
.badge {
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 10rpx;
  border-radius: 16rpx;
  background: #ff4d4f;
  color: #fff;
  font-size: 22rpx;
  line-height: 32rpx;
  text-align: center;
}
```

（实现时先读 profile.wxml/wxss，复用已有行样式类名，不强行新建。）

- [ ] **Step 4: 手工验证（微信开发者工具 + 本地后端）**

开发者工具编译：登录 → profile 页见"我的通知"入口；另一账号建单后进入通知页下拉刷新可见 SHEET_SHARED 未读（加粗+蓝点）→ 点击跳清单详情 → 返回通知页该项变已读 → profile 角标随 unreadCount 消失。

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: miniapp notifications page with profile entry badge"
```

---

## Task 7: 部署配置 + 文档收尾 + 全量回归

**Files:**
- Modify: `deploy/docker-compose.yml`（app 环境变量透传）
- Modify: `README.md`
- Modify: `docs/TODO.md`（勾选 P2 项）
- Modify: `docs/PROJECT-STATUS.md`（路线图 P2 置 ✅、更新"下一步"指向 P3、记录新延后项）

**Interfaces:**
- Consumes: Task 3/4 的 4 个环境变量名

- [ ] **Step 1: compose 透传环境变量**

`deploy/docker-compose.yml` app 服务 `environment:` 中 `DEEPSEEK_API_KEY` 行后追加（占位符默认值模式，缺省可启动）：

```yaml
      FEISHU_NOTIFY_ENABLED: ${FEISHU_NOTIFY_ENABLED:-false}
      FEISHU_WEBHOOK_URL: ${FEISHU_WEBHOOK_URL:-}
      WX_SUBSCRIBE_ENABLED: ${WX_SUBSCRIBE_ENABLED:-false}
      WX_SUBSCRIBE_TEMPLATE_ID: ${WX_SUBSCRIBE_TEMPLATE_ID:-}
```

- [ ] **Step 2: 本地全栈冒烟（降级路径）**

Run: `cd deploy && docker compose up -d --build && sleep 10 && curl -s http://localhost/api/health`
Expected: health 返回 code 0；app 日志（`docker compose logs app --tail 50`）无通道相关 ERROR（两通道默认禁用，验证"缺配置降级不报错"）。

若 🧑 已配置飞书测试群 Webhook：在 `deploy/.env` 加 `FEISHU_NOTIFY_ENABLED=true` 与 `FEISHU_WEBHOOK_URL=https://...`，`docker compose up -d` 重启后走一遍"Web 建单 → 小程序认领 → 收单"，确认飞书群收到 3 条摘要；未配置则跳过（留待 🧑）。

- [ ] **Step 3: 全量回归**

Run: `cd server && mvn clean test`
Expected: 全部 PASS（P0/P1 的 24 个 + P2 新增）

- [ ] **Step 4: 文档更新**

- `README.md`：P2 功能说明 + 4 个环境变量表（名称、作用、默认值、缺省行为）
- `docs/TODO.md`：勾选第 2 节 P2 已完成项；"随手记录区"补充待办——🧑 开通微信订阅消息模板后需单独小迭代（订阅授权埋点 + wx-subscribe 模板字段映射真机验证）；🧑 飞书群机器人 Webhook 未配置则保留第 0 节待办
- `docs/PROJECT-STATUS.md`：路线图 P2 置 ✅（注明日期与测试数）；第 5 节"已知延后项"追加"Bark/Server酱通道延后、小程序订阅授权埋点延后（等模板开通）"；第 6 节"下一步"改为 P3 AI 菜谱引擎

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "chore: compose env for notify channels and docs update for P2"
```

- [ ] **Step 6: 推送分支**

推送当前分支（分支名 `p2-notification-20260917`）到 origin，供全分支审查。

```bash
git push -u origin p2-notification-20260917
```

---

## 收尾（本计划之外，由主会话执行）

- 全分支审查（requesting-code-review）→ 修复 → 合并 main → 更新 PROJECT-STATUS/TODO
