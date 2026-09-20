package com.linklife.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestClientException;

class AuthControllerTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    private String wxLoginBody() {
        return "{\"code\":\"js-code-1\"}";
    }

    @Test
    void wxLoginCreatesUserAndReturnsTokens() throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession("openid-new-1", "unionid-1"));

        mockMvc.perform(post("/api/auth/wx-login")
                        .header("X-Real-IP", "203.0.113.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wxLoginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").isNumber());
    }

    @Test
    void wxLoginMapsTransportErrorTo401() throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenThrow(new RestClientException("connection refused"));

        mockMvc.perform(post("/api/auth/wx-login")
                        .header("X-Real-IP", "203.0.113.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wxLoginBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    void refreshReturnsNewTokenPair() throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession("openid-new-2", "unionid-2"));

        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .header("X-Real-IP", "203.0.113.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wxLoginBody()))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = com.jayway.jsonpath.JsonPath.read(
                login.getResponse().getContentAsString(), "$.data.refreshToken");

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Real-IP", "203.0.113.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void refreshRejectsGarbageTokenWith2002() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Real-IP", "203.0.113.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-jwt\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    void accessTokenCannotRefresh() throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession("openid-new-3", "unionid-3"));

        MvcResult login = mockMvc.perform(post("/api/auth/wx-login")
                        .header("X-Real-IP", "203.0.113.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wxLoginBody()))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = com.jayway.jsonpath.JsonPath.read(
                login.getResponse().getContentAsString(), "$.data.accessToken");

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Real-IP", "203.0.113.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + accessToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(2002));
    }
}
