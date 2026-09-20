package com.linklife.recipe;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipeVersion;
import com.linklife.recipe.mapper.RecipeMapper;
import com.linklife.recipe.mapper.RecipeVersionMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 手动编辑菜谱时 content 必须通过 RecipeContent.validate()（ingredients/steps 非空）。 */
class RecipeEditValidateTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private RecipeMapper recipeMapper;
    @Autowired
    private RecipeVersionMapper recipeVersionMapper;

    JdbcTemplate jdbc;
    long recipeId;
    long circleId;

    @BeforeEach
    void seed() throws Exception {
        jdbc = context.getBean(JdbcTemplate.class);
        String invite = "INV" + Long.toString(System.nanoTime(), 36).toUpperCase();
        jdbc.update("INSERT INTO circle (name, owner_id, invite_code) VALUES (?, 1, ?)",
                "校验圈" + System.nanoTime(), invite);
        circleId = jdbc.queryForObject("SELECT id FROM circle WHERE invite_code = ?",
                Long.class, invite);
        jdbc.update("INSERT INTO dish (circle_id, user_id, name) VALUES (?, 1, '番茄炒蛋')", circleId);
        long dishId = jdbc.queryForObject(
                "SELECT id FROM dish WHERE circle_id = ? AND name = '番茄炒蛋'", Long.class, circleId);

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
    }

    private String tokenOfMember(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult result = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"c\"}"))
                .andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.accessToken");
        Number userId = JsonPath.read(body, "$.data.user.id");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM circle_member WHERE circle_id = ? AND user_id = ?",
                Integer.class, circleId, userId.longValue());
        if (count == null || count == 0) {
            jdbc.update("INSERT INTO circle_member (circle_id, user_id, role) VALUES (?, ?, 'OWNER')",
                    circleId, userId.longValue());
        }
        return token;
    }

    @Test
    void editWithEmptyIngredientsRejected() throws Exception {
        String token = tokenOfMember("edit-validate-1");
        mockMvc.perform(put("/api/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":{\"servings\":2,\"totalMinutes\":30,"
                                + "\"ingredients\":[],\"seasonings\":[],"
                                + "\"steps\":[{\"no\":1,\"text\":\"打蛋\",\"durationSec\":60}],"
                                + "\"tips\":\"\"},\"changeNote\":\"x\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(5004));
    }

    @Test
    void editWithEmptyStepsRejected() throws Exception {
        String token = tokenOfMember("edit-validate-2");
        mockMvc.perform(put("/api/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":{\"servings\":2,\"totalMinutes\":30,"
                                + "\"ingredients\":[{\"name\":\"鸡蛋\",\"amount\":\"3个\"}],"
                                + "\"seasonings\":[],\"steps\":[],\"tips\":\"\"},"
                                + "\"changeNote\":\"x\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(5004));
    }

    @Test
    void editWithValidContentStillAccepted() throws Exception {
        String token = tokenOfMember("edit-validate-3");
        mockMvc.perform(put("/api/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":" + RecipeApiTest.CONTENT
                                + ",\"changeNote\":\"合法编辑\"}"))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(2,
                recipeMapper.selectById(recipeId).getCurrentVersion());
    }
}
