package com.linklife.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

class SheetCacheTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService orderService;

    long userId;

    private String login(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .header("X-Real-IP", "203.0.113.21")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = login.getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.accessToken");
        userId = Long.parseLong(JsonPath.read(body, "$.data.user.id").toString());
        return token;
    }

    private long createCircle(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/circles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"缓存圈\"}"))
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
        return Long.parseLong(JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id").toString());
    }

    private long firstItemId(long sheetId, String token) throws Exception {
        MvcResult detail = mockMvc.perform(get("/api/order/sheets/" + sheetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(detail.getResponse().getContentAsString(),
                "$.data.items[0].id").toString());
    }

    @Test
    void loadDetailIsCachedAndEvictedOnWrite() throws Exception {
        String token = login("cache-alice-1");
        long circleId = createCircle(token);
        long sheetId = createSheet(token, circleId, "缓存清单");
        long itemId = firstItemId(sheetId, token);

        // 1) 首次加载(填缓存),二次加载应命中同一实例
        var first = orderService.loadDetail(sheetId);
        var second = orderService.loadDetail(sheetId);
        assertSame(first, second);

        // 2) 写操作逐出:claim 后 loadDetail 返回新实例(状态已变)
        orderService.claim(userId, itemId);
        var third = orderService.loadDetail(sheetId);
        assertNotSame(first, third);
        assertEquals("CLAIMED", third.items().get(0).itemStatus());
        assertEquals(userId, third.items().get(0).claimantId());
    }
}
