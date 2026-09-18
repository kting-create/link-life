package com.linklife.recipe;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.linklife.IntegrationTestBase;
import com.linklife.ai.gateway.AiCallLogger;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipeVersion;
import com.linklife.recipe.mapper.RecipeMapper;
import com.linklife.recipe.mapper.RecipeVersionMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class PhotoAnalysisApiTest extends IntegrationTestBase {

    static final String VISION_JSON = "{\"advice\":\"盐放多了，建议减半\","
            + "\"changes\":[{\"stepNo\":1,\"text\":\"放盐半勺\",\"durationSec\":30}]}";
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3, 4, 5, 6, 7, 8};

    @TestConfiguration
    static class FakeVisionConfig {
        @Bean
        @Primary
        ChatClient visionChatClient() {
            return Mockito.mock(ChatClient.class,
                    Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        }
    }

    @MockBean
    private WeChatClient weChatClient;
    @MockBean
    private AiCallLogger aiCallLogger;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private ChatClient visionClient;
    @Autowired
    private RecipeMapper recipeMapper;
    @Autowired
    private RecipeVersionMapper recipeVersionMapper;

    JdbcTemplate jdbc;
    long recipeId;
    long circleId;
    long photoId;
    String token;

    @BeforeEach
    void seed() throws Exception {
        jdbc = context.getBean(JdbcTemplate.class);
        String invite = "INV" + Long.toString(System.nanoTime(), 36).toUpperCase();
        jdbc.update("INSERT INTO circle (name, owner_id, invite_code) VALUES (?, 1, ?)",
                "分析圈" + System.nanoTime(), invite);
        circleId = jdbc.queryForObject("SELECT id FROM circle WHERE invite_code = ?",
                Long.class, invite);
        jdbc.update("INSERT INTO dish (circle_id, user_id, name) VALUES (?, 1, '红烧肉')", circleId);
        long dishId = jdbc.queryForObject(
                "SELECT id FROM dish WHERE circle_id = ? AND name = '红烧肉'", Long.class, circleId);
        Recipe recipe = new Recipe();
        recipe.setDishId(dishId);
        recipe.setCurrentVersion(1);
        recipe.setCreatedBy(1L);
        recipe.setCreatedAt(LocalDateTime.now());
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.insert(recipe);
        recipeId = recipe.getId();
        jdbc.update("UPDATE dish SET recipe_id = ? WHERE id = ?", recipeId, dishId);
        RecipeVersion v = new RecipeVersion();
        v.setRecipeId(recipeId);
        v.setVersion(1);
        v.setSource("AI_GENERATE");
        v.setContent(RecipeApiTest.CONTENT);
        v.setCreatedBy(1L);
        v.setCreatedAt(LocalDateTime.now());
        recipeVersionMapper.insert(v);
        token = login();
        MvcResult up = mockMvc.perform(multipart(
                        "/api/recipes/" + recipeId + "/steps/1/photos")
                        .file(new MockMultipartFile("file", "a.jpg",
                                MediaType.IMAGE_JPEG_VALUE, JPG))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        photoId = Long.parseLong(JsonPath.read(
                up.getResponse().getContentAsString(), "$.data.id").toString());
    }

    private String login() throws Exception {
        Mockito.when(weChatClient.code2Session(Mockito.anyString()))
                .thenReturn(new WxSession("analysis-user", "unionid-analysis-user"));
        MvcResult result = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"c\"}"))
                .andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        Number userId = JsonPath.read(body, "$.data.user.id");
        jdbc.update("INSERT INTO circle_member (circle_id, user_id, role) VALUES (?, ?, 'OWNER')",
                circleId, userId.longValue());
        return JsonPath.read(body, "$.data.accessToken");
    }

    private static ChatResponse textResponse(String text) {
        ChatResponse response = Mockito.mock(ChatResponse.class,
                Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        Mockito.doReturn(new Generation(new AssistantMessage(text))).when(response).getResult();
        return response;
    }

    @Test
    void analyzeStoresResultAndReturnsAdvice() throws Exception {
        ChatResponse response = textResponse(VISION_JSON);
        Mockito.when(visionClient.prompt(Mockito.any(Prompt.class)).call().chatResponse())
                .thenReturn(response);

        mockMvc.perform(post("/api/photos/" + photoId + "/analysis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.advice").value("盐放多了，建议减半"))
                .andExpect(jsonPath("$.data.changes[0].stepNo").value(1));    }

    @Test
    void analyzeWrapsAiFailureAs6005() throws Exception {
        Mockito.when(visionClient.prompt(Mockito.any(Prompt.class)).call().chatResponse())
                .thenThrow(new RuntimeException("dashscope down"));

        mockMvc.perform(post("/api/photos/" + photoId + "/analysis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(6005));
    }

    @Test
    void analyzeWrapsParseFailureAs6005() throws Exception {
        ChatResponse response = textResponse("这不是 JSON");
        Mockito.when(visionClient.prompt(Mockito.any(Prompt.class)).call().chatResponse())
                .thenReturn(response);

        mockMvc.perform(post("/api/photos/" + photoId + "/analysis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(6005));
    }
}
