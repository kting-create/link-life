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

class SheetCompleteAndShareTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    private String login(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .header("X-Real-IP", "203.0.113.22")
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

    private void join(String ownerToken, String memberToken, long circleId) throws Exception {
        MvcResult list = mockMvc.perform(get("/api/circles")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        String inviteCode = ((java.util.List<?>) JsonPath.read(list.getResponse().getContentAsString(),
                "$.data[?(@.id == " + circleId + ")].inviteCode")).get(0).toString();
        mockMvc.perform(post("/api/circles/join")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isOk());
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

    private String getShareToken(long sheetId, String token) throws Exception {
        MvcResult detail = mockMvc.perform(get("/api/order/sheets/" + sheetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(detail.getResponse().getContentAsString(), "$.data.shareToken");
    }

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
}
