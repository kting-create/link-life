package com.linklife.recipe;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

class PantryApiTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    private String login(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult result = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    void addListDeletePantryItem() throws Exception {
        String token = login("pantry-user-1");

        MvcResult created = mockMvc.perform(post("/api/me/pantry")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SEASONING\",\"name\":\"生抽\",\"note\":\"家用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("生抽"))
                .andReturn();
        String itemId = JsonPath.read(created.getResponse().getContentAsString(),
                "$.data.id").toString();

        mockMvc.perform(get("/api/me/pantry")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("SEASONING"));

        mockMvc.perform(delete("/api/me/pantry/" + itemId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/pantry")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void duplicateNameRejected() throws Exception {
        String token = login("pantry-user-2");
        String body = "{\"type\":\"SEASONING\",\"name\":\"老抽\"}";
        mockMvc.perform(post("/api/me/pantry")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/me/pantry")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5005));
    }

    @Test
    void deleteOthersItemRejected() throws Exception {
        String owner = login("pantry-owner");
        String stranger = login("pantry-stranger");
        MvcResult created = mockMvc.perform(post("/api/me/pantry")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"INGREDIENT\",\"name\":\"五花肉\"}"))
                .andExpect(status().isOk()).andReturn();
        String itemId = JsonPath.read(created.getResponse().getContentAsString(),
                "$.data.id").toString();

        mockMvc.perform(delete("/api/me/pantry/" + itemId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(5007));
    }

    @Test
    void invalidTypeRejected() throws Exception {
        String token = login("pantry-user-3");
        mockMvc.perform(post("/api/me/pantry")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"OTHER\",\"name\":\"未知\"}"))
                .andExpect(status().isBadRequest());
    }
}
