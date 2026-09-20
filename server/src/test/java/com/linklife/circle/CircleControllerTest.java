package com.linklife.circle;

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

class CircleControllerTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    private String login(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .header("X-Real-IP", "203.0.113.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private long createCircle(String token, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/circles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return Long.parseLong(JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id").toString());
    }

    @Test
    void createCircleThenOwnerIsMember() throws Exception {
        String token = login("circle-owner-1");
        long circleId = createCircle(token, "我们家");
        mockMvc.perform(get("/api/circles/" + circleId + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].role").value("OWNER"));

        mockMvc.perform(get("/api/circles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("我们家"))
                .andExpect(jsonPath("$.data[0].inviteCode").isNotEmpty());
    }

    @Test
    void joinWithInviteCode() throws Exception {
        String ownerToken = login("circle-owner-2");
        MvcResult created = mockMvc.perform(post("/api/circles")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"朋友圈\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String response = created.getResponse().getContentAsString();
        String inviteCode = JsonPath.read(response, "$.data.inviteCode");
        long circleId = Long.parseLong(JsonPath.read(response, "$.data.id").toString());

        String memberToken = login("circle-member-2");
        mockMvc.perform(post("/api/circles/join")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("朋友圈"));

        // 重复加入报错
        mockMvc.perform(post("/api/circles/join")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1004));

        // 无效邀请码
        mockMvc.perform(post("/api/circles/join")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"NOEXIST1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1003));

        // 成员列表两人
        mockMvc.perform(get("/api/circles/" + circleId + "/members")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void nonMemberCannotSeeMembers() throws Exception {
        String ownerToken = login("circle-owner-3");
        long circleId = createCircle(ownerToken, "圈子3");

        String outsiderToken = login("circle-outsider-3");
        mockMvc.perform(get("/api/circles/" + circleId + "/members")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1002));

        // 不存在的圈子
        mockMvc.perform(get("/api/circles/999999/members")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
