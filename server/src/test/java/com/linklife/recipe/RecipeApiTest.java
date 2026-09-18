package com.linklife.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipeFeedback;
import com.linklife.recipe.entity.RecipeVersion;
import com.linklife.recipe.mapper.RecipeFeedbackMapper;
import com.linklife.recipe.mapper.RecipeMapper;
import com.linklife.recipe.mapper.RecipeVersionMapper;
import com.linklife.recipe.service.RecipeService;
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

class RecipeApiTest extends IntegrationTestBase {

    static final String CONTENT = "{\"servings\":2,\"totalMinutes\":30,"
            + "\"ingredients\":[{\"name\":\"鸡蛋\",\"amount\":\"3个\"}],\"seasonings\":[],"
            + "\"steps\":[{\"no\":1,\"text\":\"打蛋\",\"durationSec\":60}],\"tips\":\"\"}";

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
    @Autowired
    private RecipeFeedbackMapper recipeFeedbackMapper;

    JdbcTemplate jdbc;
    long recipeId;
    long circleId;

    @BeforeEach
    void seed() throws Exception {
        jdbc = context.getBean(JdbcTemplate.class);
        // 列名按 V1 迁移真实 schema 修正
        String invite = "INV" + Long.toString(System.nanoTime(), 36).toUpperCase();
        jdbc.update("INSERT INTO circle (name, owner_id, invite_code) VALUES (?, 1, ?)",
                "菜谱圈" + System.nanoTime(), invite);
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
        insertVersion(1, "AI_GENERATE");
    }

    void insertVersion(int version, String source) {
        RecipeVersion v = new RecipeVersion();
        v.setRecipeId(recipeId);
        v.setVersion(version);
        v.setSource(source);
        v.setContent(CONTENT);
        v.setCreatedBy(1L);
        v.setCreatedAt(LocalDateTime.now());
        recipeVersionMapper.insert(v);
    }

    /** 登录并把该用户补进测试圈（OWNER），返回 token。 */
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
    void getDetailReturnsCurrentVersion() throws Exception {
        String token = tokenOfMember("recipe-owner");
        mockMvc.perform(get("/api/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVersion").value(1))
                .andExpect(jsonPath("$.data.dishName").value("红烧肉"))
                .andExpect(jsonPath("$.data.content.servings").value(2))
                .andExpect(jsonPath("$.data.versions.length()").value(1));
    }

    @Test
    void outsiderGetsNotFound() throws Exception {
        String outsider = tokenOfOutsider("recipe-outsider");
        mockMvc.perform(get("/api/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(5001));
    }

    /** 登录但 NOT 加入圈。 */
    private String tokenOfOutsider(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult result = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"c\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    void feedbackAddsRow() throws Exception {
        String token = tokenOfMember("recipe-owner");
        mockMvc.perform(post("/api/recipes/" + recipeId + "/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":3,\"comment\":\"偏淡了\"}"))
                .andExpect(status().isOk());
        assertEquals(1, recipeFeedbackMapper.selectCount(
                new LambdaQueryWrapper<RecipeFeedback>()
                        .eq(RecipeFeedback::getRecipeId, recipeId)));
    }

    @Test
    void feedbackScoreValidation() throws Exception {
        String token = tokenOfMember("recipe-owner");
        mockMvc.perform(post("/api/recipes/" + recipeId + "/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":0,\"comment\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editContentAddsManualVersionAndMovesPointer() throws Exception {
        String token = tokenOfMember("recipe-owner");
        mockMvc.perform(put("/api/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":" + CONTENT + ",\"changeNote\":\"手动加了一步\"}"))
                .andExpect(status().isOk());
        assertEquals(2, recipeMapper.selectById(recipeId).getCurrentVersion());
        assertEquals(2, recipeVersionMapper.selectCount(
                new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipeId)));
    }

    @Test
    void editCustomNameOnlyDoesNotAddVersion() throws Exception {
        String token = tokenOfMember("recipe-owner");
        mockMvc.perform(put("/api/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"我妈的红烧肉\"}"))
                .andExpect(status().isOk());
        assertEquals(1, recipeVersionMapper.selectCount(
                new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipeId)));
        mockMvc.perform(get("/api/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.customName").value("我妈的红烧肉"));
    }

    @Test
    void editBlockedAtVersionLimit() throws Exception {
        for (int v = 2; v <= RecipeService.MAX_VERSIONS; v++) {
            insertVersion(v, "AI_ITERATE");
        }
        String token = tokenOfMember("recipe-owner");
        mockMvc.perform(put("/api/recipes/" + recipeId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":" + CONTENT + ",\"changeNote\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(5002));
    }

    @Test
    void rollbackMovesPointerWithoutNewVersion() throws Exception {
        insertVersion(2, "AI_ITERATE");
        String token = tokenOfMember("recipe-owner");
        mockMvc.perform(post("/api/recipes/" + recipeId + "/rollback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk());
        assertEquals(1, recipeMapper.selectById(recipeId).getCurrentVersion());
        assertEquals(2, recipeVersionMapper.selectCount(
                new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipeId)));
    }

    @Test
    void rollbackToMissingVersionRejected() throws Exception {
        String token = tokenOfMember("recipe-owner");
        mockMvc.perform(post("/api/recipes/" + recipeId + "/rollback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":9}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void byDishLookup() throws Exception {
        String token = tokenOfMember("recipe-owner");
        mockMvc.perform(get("/api/recipes/by-dish")
                        .header("Authorization", "Bearer " + token)
                        .param("circleId", String.valueOf(circleId))
                        .param("dishName", "红烧肉"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value((int) recipeId));
        mockMvc.perform(get("/api/recipes/by-dish")
                        .header("Authorization", "Bearer " + token)
                        .param("circleId", String.valueOf(circleId))
                        .param("dishName", "不存在的菜"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(5001));
    }
}
