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
