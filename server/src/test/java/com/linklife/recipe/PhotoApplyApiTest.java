package com.linklife.recipe;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipePhoto;
import com.linklife.recipe.entity.RecipeVersion;
import com.linklife.recipe.dto.RecipeContent;
import com.linklife.recipe.mapper.RecipeMapper;
import com.linklife.recipe.mapper.RecipePhotoMapper;
import com.linklife.recipe.mapper.RecipeVersionMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class PhotoApplyApiTest extends IntegrationTestBase {

    static final String ANALYSIS_JSON = "{\"advice\":\"盐放多了，建议减半\","
            + "\"changes\":[{\"stepNo\":1,\"text\":\"放盐半勺\",\"durationSec\":30}]}";

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
    private RecipePhotoMapper photoMapper;

    JdbcTemplate jdbc;
    long recipeId;
    long circleId;
    long photoId;
    String token;
    long currentUserId;

    @BeforeEach
    void seed() throws Exception {
        jdbc = context.getBean(JdbcTemplate.class);
        String invite = "INV" + Long.toString(System.nanoTime(), 36).toUpperCase();
        jdbc.update("INSERT INTO circle (name, owner_id, invite_code) VALUES (?, 1, ?)",
                "应用圈" + System.nanoTime(), invite);
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
        RecipePhoto photo = new RecipePhoto();
        photo.setRecipeId(recipeId);
        photo.setStepNo(1);
        photo.setUploaderId(currentUserId);
        photo.setFilePath("images/recipes/" + recipeId + "/seed.jpg");
        photo.setSizeBytes(12L);
        photo.setAnalysis(ANALYSIS_JSON);
        photo.setAnalyzedAt(LocalDateTime.now());
        photo.setCreatedAt(LocalDateTime.now());
        photoMapper.insert(photo);
        photoId = photo.getId();
    }

    private String login() throws Exception {
        Mockito.when(weChatClient.code2Session(Mockito.anyString()))
                .thenReturn(new WxSession("apply-user", "unionid-apply-user"));
        MvcResult result = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"c\"}"))
                .andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        currentUserId = Long.parseLong(JsonPath.read(body, "$.data.user.id").toString());
        jdbc.update("INSERT INTO circle_member (circle_id, user_id, role) VALUES (?, ?, 'OWNER')",
                circleId, currentUserId);
        return JsonPath.read(body, "$.data.accessToken");
    }

    @Test
    void applyCreatesPhotoAnalysisVersion() throws Exception {
        mockMvc.perform(post("/api/photos/" + photoId + "/apply")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));

        Recipe recipe = recipeMapper.selectById(recipeId);
        org.junit.jupiter.api.Assertions.assertEquals(2, recipe.getCurrentVersion());
        RecipeVersion v2 = recipeVersionMapper.selectOne(
                new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipeId)
                        .eq(RecipeVersion::getVersion, 2));
        org.junit.jupiter.api.Assertions.assertEquals("PHOTO_ANALYSIS", v2.getSource());
        org.junit.jupiter.api.Assertions.assertEquals("盐放多了，建议减半", v2.getChangeNote());
        // MySQL JSON 列会将 JSON 规范化为 "key": value（冒号带空格），故解析后断言而非子串匹配
        RecipeContent merged = com.linklife.recipe.service.RecipeService.parseContent(v2.getContent());
        org.junit.jupiter.api.Assertions.assertEquals("放盐半勺", merged.steps().get(0).text());
        org.junit.jupiter.api.Assertions.assertEquals(30, merged.steps().get(0).durationSec());
    }

    @Test
    void applyWithoutAnalysisReturns6006() throws Exception {
        // MyBatis-Plus updateById 默认跳过 null 字段，须用 SQL 显式置空 analysis
        jdbc.update("UPDATE recipe_photo SET analysis = NULL WHERE id = ?", photoId);
        mockMvc.perform(post("/api/photos/" + photoId + "/apply")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(6006));
    }

    @Test
    void applyRespectsVersionLimit() throws Exception {
        for (int i = 2; i <= 5; i++) {
            RecipeVersion v = new RecipeVersion();
            v.setRecipeId(recipeId);
            v.setVersion(i);
            v.setSource("MANUAL_EDIT");
            v.setContent(RecipeApiTest.CONTENT);
            v.setChangeNote("v" + i);
            v.setCreatedBy(currentUserId);
            v.setCreatedAt(LocalDateTime.now());
            recipeVersionMapper.insert(v);
        }
        jdbc.update("UPDATE recipe SET current_version = 5 WHERE id = ?", recipeId);
        mockMvc.perform(post("/api/photos/" + photoId + "/apply")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(5002));
    }
}
