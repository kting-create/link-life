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
                        .header("X-Real-IP", "203.0.113.19")
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
