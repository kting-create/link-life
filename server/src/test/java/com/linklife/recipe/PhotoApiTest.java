package com.linklife.recipe;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.linklife.recipe.service.PhotoService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PhotoApiTest extends IntegrationTestBase {

    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3, 4, 5, 6, 7, 8};

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
    private PhotoService photoService;

    JdbcTemplate jdbc;
    long recipeId;
    long circleId;

    @BeforeEach
    void seed() throws Exception {
        jdbc = context.getBean(JdbcTemplate.class);
        String invite = "INV" + Long.toString(System.nanoTime(), 36).toUpperCase();
        jdbc.update("INSERT INTO circle (name, owner_id, invite_code) VALUES (?, 1, ?)",
                "拍照圈" + System.nanoTime(), invite);
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
    }

    private String tokenOfMember(String openid) throws Exception {
        Mockito.when(weChatClient.code2Session(Mockito.anyString()))
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

    /** 登录但 NOT 加入圈。 */
    private String tokenOfOutsider(String openid) throws Exception {
        Mockito.when(weChatClient.code2Session(Mockito.anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        MvcResult result = mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"c\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    @Test
    void uploadListDeleteRoundTrip() throws Exception {
        String token = tokenOfMember("photo-user");
        String url = "/api/recipes/" + recipeId + "/steps/1/photos";
        MvcResult up = mockMvc.perform(multipart(url)
                        .file(new MockMultipartFile("file", "step.jpg", MediaType.IMAGE_JPEG_VALUE, JPG))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.stepNo").value(1))
                .andExpect(jsonPath("$.data.url").value(
                        org.hamcrest.Matchers.matchesPattern("/images/recipes/\\d+/.+\\.jpg")))
                .andReturn();
        long photoId = Long.parseLong(JsonPath.read(
                up.getResponse().getContentAsString(), "$.data.id").toString());

        mockMvc.perform(get("/api/recipes/" + recipeId + "/photos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].analyzed").value(false));

        mockMvc.perform(delete("/api/photos/" + photoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/recipes/" + recipeId + "/photos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void uploadRejectsBadTypeAndInvalidStep() throws Exception {
        String token = tokenOfMember("photo-user-2");
        mockMvc.perform(multipart("/api/recipes/" + recipeId + "/steps/1/photos")
                        .file(new MockMultipartFile("file", "evil.gif", "image/gif",
                                new byte[]{'G', 'I', 'F', '8', 9, 0, 1, 2, 3, 4, 5, 6}))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6001));
        mockMvc.perform(multipart("/api/recipes/" + recipeId + "/steps/1/photos")
                        .file(new MockMultipartFile("file", "fake.jpg", MediaType.IMAGE_JPEG_VALUE,
                                "hello world jpg".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(6001));
        mockMvc.perform(multipart("/api/recipes/" + recipeId + "/steps/99/photos")
                        .file(new MockMultipartFile("file", "ok.jpg", MediaType.IMAGE_JPEG_VALUE, JPG))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(6008));
    }

    @Test
    void uploadRejectsOversize() throws Exception {
        String token = tokenOfMember("photo-user-3");
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        big[0] = (byte) 0xFF;
        big[1] = (byte) 0xD8;
        big[2] = (byte) 0xFF;
        mockMvc.perform(multipart("/api/recipes/" + recipeId + "/steps/1/photos")
                        .file(new MockMultipartFile("file", "big.jpg", MediaType.IMAGE_JPEG_VALUE, big))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(6002));
    }

    @Test
    void outsiderSeesNothingAndCannotDelete() throws Exception {
        String member = tokenOfMember("photo-owner");
        MvcResult up = mockMvc.perform(multipart("/api/recipes/" + recipeId + "/steps/1/photos")
                        .file(new MockMultipartFile("file", "a.jpg", MediaType.IMAGE_JPEG_VALUE, JPG))
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isOk()).andReturn();
        long photoId = Long.parseLong(JsonPath.read(
                up.getResponse().getContentAsString(), "$.data.id").toString());
        String outsider = tokenOfOutsider("photo-outsider");
        mockMvc.perform(get("/api/recipes/" + recipeId + "/photos")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(delete("/api/photos/" + photoId)
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(jsonPath("$.code").value(5001));
    }

    @Test
    void memberCannotDeleteOthersPhoto() throws Exception {
        String uploader = tokenOfMember("photo-member-b");
        MvcResult up = mockMvc.perform(multipart("/api/recipes/" + recipeId + "/steps/1/photos")
                        .file(new MockMultipartFile("file", "b.jpg", MediaType.IMAGE_JPEG_VALUE, JPG))
                        .header("Authorization", "Bearer " + uploader))
                .andExpect(status().isOk()).andReturn();
        long photoId = Long.parseLong(JsonPath.read(
                up.getResponse().getContentAsString(), "$.data.id").toString());
        String anotherMember = tokenOfMember("photo-member-c");
        mockMvc.perform(delete("/api/photos/" + photoId)
                        .header("Authorization", "Bearer " + anotherMember))
                .andExpect(jsonPath("$.code").value(6007));
    }

    @Test
    void circleOwnerCanDeleteOthersPhoto() throws Exception {
        String uploader = tokenOfMember("photo-member-d");
        MvcResult up = mockMvc.perform(multipart("/api/recipes/" + recipeId + "/steps/1/photos")
                        .file(new MockMultipartFile("file", "d.jpg", MediaType.IMAGE_JPEG_VALUE, JPG))
                        .header("Authorization", "Bearer " + uploader))
                .andExpect(status().isOk()).andReturn();
        long photoId = Long.parseLong(JsonPath.read(
                up.getResponse().getContentAsString(), "$.data.id").toString());
        jdbc.update("INSERT INTO circle_member (circle_id, user_id, role) VALUES (?, 1, 'OWNER')",
                circleId);
        assertDoesNotThrow(() -> photoService.delete(1L, photoId));
        Number count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recipe_photo WHERE id = ?", Number.class, photoId);
        org.junit.jupiter.api.Assertions.assertEquals(0, count.intValue());
    }

    @Test
    void uploadRejectsWhenPhotoLimitReached() throws Exception {
        String token = tokenOfMember("photo-limit-user");
        String url = "/api/recipes/" + recipeId + "/steps/1/photos";
        for (int i = 0; i < PhotoService.MAX_PHOTOS_PER_RECIPE; i++) {
            mockMvc.perform(multipart(url)
                            .file(new MockMultipartFile("file", "p" + i + ".jpg",
                                    MediaType.IMAGE_JPEG_VALUE, JPG))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(multipart(url)
                        .file(new MockMultipartFile("file", "over.jpg",
                                MediaType.IMAGE_JPEG_VALUE, JPG))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(6003));
    }
}
