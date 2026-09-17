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
    void longSheetTitleStillDeliversNotifications() throws Exception {
        String alice = login("evt-alice-4");
        String bob = login("evt-bob-4");
        long circleId = createCircle(alice);
        join(bob, inviteCode(alice, circleId));

        String longTitle = "长标题测试".repeat(12); // 60 chars, within @Size(max=64)
        createSheet(alice, circleId, longTitle);

        awaitUnread(bob, 1);
        MvcResult list = mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("SHEET_SHARED"))
                .andReturn();
        String title = JsonPath.read(list.getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                "$.data.items[0].title");
        org.junit.jupiter.api.Assertions.assertTrue(title.contains(longTitle),
                "persisted title should keep the full sheet title: " + title);
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
