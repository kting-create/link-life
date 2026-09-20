package com.linklife.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.linklife.user.dto.TasteSummary;
import com.linklife.user.service.TasteProfileService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class TasteProfileTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TasteProfileService tasteProfileService;

    private MvcResult login(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        return mockMvc.perform(post("/api/auth/wx-login")
                        .header("X-Real-IP", "203.0.113.6")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"c\"}"))
                .andExpect(status().isOk()).andReturn();
    }

    @Test
    void emptyProfileReturnsEmptyObject() throws Exception {
        String token = JsonPath.read(
                login("taste-user-1").getResponse().getContentAsString(), "$.data.accessToken");
        mockMvc.perform(get("/api/me/taste-profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap());
    }

    @Test
    void applySummaryUpsertsProfile() throws Exception {
        String body = login("taste-user-2").getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.accessToken");
        long userId = Long.parseLong(JsonPath.read(body, "$.data.user.id").toString());

        tasteProfileService.applySummary(userId,
                new TasteSummary("口味偏清淡、忌辣", List.of("清淡", "忌辣")));
        assertEquals("口味偏清淡、忌辣", tasteProfileService.getSummary(userId));

        // 覆盖式更新
        tasteProfileService.applySummary(userId, new TasteSummary("偏咸香", List.of("咸香")));
        assertEquals("偏咸香", tasteProfileService.getSummary(userId));

        // 空摘要不动画像
        tasteProfileService.applySummary(userId, null);
        assertEquals("偏咸香", tasteProfileService.getSummary(userId));

        // REST 端点读到最新值
        mockMvc.perform(get("/api/me/taste-profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("偏咸香"));
    }
}
