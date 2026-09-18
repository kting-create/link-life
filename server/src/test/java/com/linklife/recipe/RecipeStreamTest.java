package com.linklife.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jayway.jsonpath.JsonPath;
import com.linklife.IntegrationTestBase;
import com.linklife.ai.gateway.AiCallLogger;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import com.linklife.order.entity.Dish;
import com.linklife.order.mapper.DishMapper;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipeVersion;
import com.linklife.recipe.mapper.RecipeMapper;
import com.linklife.recipe.mapper.RecipeVersionMapper;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

class RecipeStreamTest extends IntegrationTestBase {

    static final String FULL_JSON = "{\"servings\":2,\"totalMinutes\":30,"
            + "\"ingredients\":[{\"name\":\"鸡蛋\",\"amount\":\"3个\"}],\"seasonings\":[],"
            + "\"steps\":[{\"no\":1,\"text\":\"打蛋\",\"durationSec\":60}],\"tips\":\"\"}";

    @TestConfiguration
    static class FakeChatConfig {
        @Bean
        @org.springframework.context.annotation.Primary
        ChatClient deepseekChatClient() {
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
    private ChatClient chatClient;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private RecipeMapper recipeMapper;
    @Autowired
    private RecipeVersionMapper recipeVersionMapper;

    private String currentOpenid;
    private long currentUserId;

    private String token(String openid) throws Exception {
        currentOpenid = openid;
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult result = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"c\"}"))
                .andExpect(status().isOk()).andReturn();
        String body = result.getResponse().getContentAsString();
        currentUserId = Long.parseLong(JsonPath.read(body, "$.data.user.id").toString());
        return JsonPath.read(body, "$.data.accessToken");
    }

    private long circleIdOf(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/circles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"流式圈\"}"))
                .andExpect(status().isOk()).andReturn();
        return Long.parseLong(JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.id").toString());
    }

    private MvcResult awaitAsync(MvcResult result) throws Exception {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
        while (result.getRequest().isAsyncStarted()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        return result;
    }

    private static String doneRecipeId(String sseBody) {
        Matcher m = Pattern.compile("\"recipeId\":(\\d+)").matcher(sseBody);
        return m.find() ? m.group(1) : "0";
    }

    @Test
    void generateStreamsDeltaThenDoneAndPersists() throws Exception {
        when(chatClient.prompt().user(anyString()).stream().content())
                .thenReturn(Flux.just("{\"serv", "ings\":2,\"totalMinutes\":30,"
                        + "\"ingredients\":[{\"name\":\"鸡蛋\",\"amount\":\"3个\"}],"
                        + "\"seasonings\":[],\"steps\":[{\"no\":1,\"text\":\"打蛋\","
                        + "\"durationSec\":60}],\"tips\":\"\"}"));
        String token = token("stream-user-1");
        long circleId = circleIdOf(token);

        MvcResult result = awaitAsync(mockMvc.perform(post("/api/recipes/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"circleId\":" + circleId + ",\"dishName\":\"番茄炒蛋\"}"))
                .andExpect(request().asyncStarted())
                .andReturn());

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:delta")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:done")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("\"recipeId\":")));

        Dish dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getCircleId, circleId).eq(Dish::getName, "番茄炒蛋"));
        assertNotNull(dish);
        assertNotNull(dish.getRecipeId());
        Recipe recipe = recipeMapper.selectById(dish.getRecipeId());
        assertEquals(1, recipe.getCurrentVersion());
        List<RecipeVersion> versions = recipeVersionMapper.selectList(
                new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipe.getId()));
        assertEquals(1, versions.size());
        assertEquals("AI_GENERATE", versions.get(0).getSource());
        Mockito.verify(aiCallLogger).log(Mockito.eq(currentUserId), anyString(),
                anyString(), anyString(), Mockito.eq(true), Mockito.isNull(),
                Mockito.isNull(), Mockito.isNull());
    }

    @Test
    void generateParseFailureEmitsErrorAndPersistsNothing() throws Exception {
        when(chatClient.prompt().user(anyString()).stream().content())
                .thenReturn(Flux.just("抱歉，我无法生成菜谱"));
        String token = token("stream-user-2");
        long circleId = circleIdOf(token);

        MvcResult result = awaitAsync(mockMvc.perform(post("/api/recipes/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"circleId\":" + circleId + ",\"dishName\":\"黑暗料理\"}"))
                .andExpect(request().asyncStarted())
                .andReturn());

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:error")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("\"code\":5004")));

        assertEquals(0, dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getCircleId, circleId).eq(Dish::getName, "黑暗料理")));
        Mockito.verify(aiCallLogger).log(Mockito.any(), anyString(), anyString(), anyString(),
                Mockito.eq(false), Mockito.contains("PERSIST_FAILED"),
                Mockito.isNull(), Mockito.isNull());
    }

    @Test
    void iterateCreatesNewVersionAndUpdatesTastePrefs() throws Exception {
        when(chatClient.prompt().user(anyString()).stream().content())
                .thenReturn(Flux.just(FULL_JSON));
        String token = token("stream-user-3");
        long circleId = circleIdOf(token);

        MvcResult genResult = awaitAsync(mockMvc.perform(post("/api/recipes/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"circleId\":" + circleId + ",\"dishName\":\"可乐鸡翅\"}"))
                .andExpect(request().asyncStarted()).andReturn());
        String genBody = mockMvc.perform(asyncDispatch(genResult))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long recipeId = Long.parseLong(doneRecipeId(genBody));

        // 提交反馈
        mockMvc.perform(post("/api/recipes/" + recipeId + "/feedback")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":2,\"comment\":\"太甜了\"}"))
                .andExpect(status().isOk());

        // 迭代：dual output JSON
        String iterationJson = "{\"recipe\":" + FULL_JSON
                + ",\"taste_summary\":{\"summary\":\"口味偏咸、忌甜\",\"tags\":[\"咸\",\"忌甜\"]}}";
        when(chatClient.prompt().user(anyString()).stream().content())
                .thenReturn(Flux.just(iterationJson));
        MvcResult iterResult = awaitAsync(mockMvc.perform(
                        post("/api/recipes/" + recipeId + "/iterate")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"做咸一点\"}"))
                .andExpect(request().asyncStarted()).andReturn());
        mockMvc.perform(asyncDispatch(iterResult))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:done")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("\"version\":2")));

        Recipe recipe = recipeMapper.selectById(recipeId);
        assertEquals(2, recipe.getCurrentVersion());
        // taste_prefs 已沉淀
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        String tastePrefs = jdbc.queryForObject(
                "SELECT taste_prefs FROM user_profile WHERE user_id = ?",
                String.class, currentUserId);
        assertNotNull(tastePrefs);
        assertTrue(tastePrefs.contains("忌甜"));
    }

    @Test
    void generateWithAsyncFluxStillDeliversDeltasAndDone() throws Exception {
        // 异步发射：验证 emitter 不会在流结束前被提前 complete（回归：runAsync finally 提前收尾）
        when(chatClient.prompt().user(anyString()).stream().content())
                .thenReturn(Flux.just("{\"servings\":2,\"totalMinutes\":30,",
                                "\"ingredients\":[{\"name\":\"鸡蛋\",\"amount\":\"3个\"}],",
                                "\"seasonings\":[],\"steps\":[{\"no\":1,\"text\":\"打蛋\","
                                        + "\"durationSec\":60}],\"tips\":\"\"}")
                        .delayElements(java.time.Duration.ofMillis(50)));
        String token = token("stream-async");
        long circleId = circleIdOf(token);

        MvcResult result = awaitAsync(mockMvc.perform(post("/api/recipes/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"circleId\":" + circleId + ",\"dishName\":\"异步菜\"}"))
                .andExpect(request().asyncStarted())
                .andReturn());

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:delta")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("event:done")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("\"recipeId\":")));

        Dish dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getCircleId, circleId).eq(Dish::getName, "异步菜"));
        assertNotNull(dish);
        assertNotNull(dish.getRecipeId());
        assertEquals(1, recipeMapper.selectById(dish.getRecipeId()).getCurrentVersion());
    }

    @Test
    void generateRequiresMembership() throws Exception {
        String member = token("stream-member");
        long circleId = circleIdOf(member);
        String outsider = token("stream-outsider");
        mockMvc.perform(post("/api/recipes/generate")
                        .header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"circleId\":" + circleId + ",\"dishName\":\"宫保鸡丁\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1002));
    }
}
