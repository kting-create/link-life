package com.linklife.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class MeControllerTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    private String loginAndGetToken() throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession("openid-me-1", "unionid-me-1"));
        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return com.jayway.jsonpath.JsonPath.read(
                login.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        String token = loginAndGetToken();
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void updateMeChangesNickname() throws Exception {
        String token = loginAndGetToken();
        mockMvc.perform(put("/api/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"家里的主厨\",\"avatar\":\"https://x/a.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("家里的主厨"));
    }
}
