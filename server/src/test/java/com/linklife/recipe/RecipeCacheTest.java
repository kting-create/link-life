package com.linklife.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipeVersion;
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

class RecipeCacheTest extends IntegrationTestBase {

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
    private RecipeService recipeService;

    JdbcTemplate jdbc;
    long recipeId;
    long circleId;
    long userId;

    @BeforeEach
    void seed() throws Exception {
        jdbc = context.getBean(JdbcTemplate.class);
        String invite = "INV" + Long.toString(System.nanoTime(), 36).toUpperCase();
        jdbc.update("INSERT INTO circle (name, owner_id, invite_code) VALUES (?, 1, ?)",
                "缓存菜谱圈" + System.nanoTime(), invite);
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
        v.setContent(CONTENT);
        v.setCreatedBy(1L);
        v.setCreatedAt(LocalDateTime.now());
        recipeVersionMapper.insert(v);
    }

    /** 登录、把该用户补进测试圈(OWNER),记录 userId,返回 token。 */
    private String tokenOfMember(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult result = mockMvc.perform(post("/api/auth/wx-login")
                        .header("X-Real-IP", "203.0.113.13")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"c\"}"))
                .andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.accessToken");
        userId = Long.parseLong(JsonPath.read(body, "$.data.user.id").toString());
        jdbc.update("INSERT INTO circle_member (circle_id, user_id, role) VALUES (?, ?, 'OWNER')",
                circleId, userId);
        return token;
    }

    @Test
    void detailCachedAndEvictedOnEdit() throws Exception {
        tokenOfMember("cache-recipe-owner");

        // 1) 首次加载(填缓存),二次加载应命中同一实例
        var first = recipeService.loadRecipeDetail(recipeId);
        var second = recipeService.loadRecipeDetail(recipeId);
        assertSame(first, second);

        // 2) 反馈不进详情缓存内容:addFeedback 后不逐出,仍是同一引用
        recipeService.addFeedback(userId, recipeId, 3, "偏淡了");
        var third = recipeService.loadRecipeDetail(recipeId);
        assertSame(first, third);

        // 3) edit 后逐出,返回新引用且 customName 已更新
        recipeService.edit(userId, recipeId, null, "我妈的红烧肉", null);
        var fourth = recipeService.loadRecipeDetail(recipeId);
        assertNotSame(first, fourth);
        assertEquals("我妈的红烧肉", fourth.customName());
    }
}
