# P4 烹饪引导 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 P3 菜谱引擎上新增烹饪引导：双端分步计时烹饪模式、过程拍照上传（本地卷 + nginx 静态服务）、Qwen3-VL 视觉分析并经用户确认后以补丁形式生成菜谱新版本。

**Architecture:** 复用现有 recipe 模块与 `AiGatewayService`。后端新增 recipe_photo 表 + PhotoService/PhotoAnalysisService；AI 侧引入 spring-ai-starter-model-openai 指向阿里百炼 OpenAI 兼容端点，与 deepseek starter 双 ChatModel 并存（`spring.ai.chat.client.enabled=false` + 手动 ChatClient bean）。前端小程序新增 cook-mode 页（亮屏常亮/计时/拍照/分析），Web 新增 CookMode 页。

**Tech Stack:** Spring Boot 3.3.4 + Spring AI 1.0.0（deepseek + openai starter）、MyBatis-Plus、Flyway、微信小程序原生、Vue 3 + Vite。

**Spec:** `docs/superpowers/specs/2026-09-18-p4-cooking-guide-design.md`

## Global Constraints

- 分支：`feature/p3-recipe-engine`，**不新建分支**；每任务一 commit。
- Java 17 / Spring Boot 3.3.4 / Spring AI 1.0.0（BOM 已在 `server/pom.xml` dependencyManagement）。
- 统一响应 `Result<T>`（code=0 成功）；新错误码走 `common/exception/ErrorCode.java` 6xxx 段。
- AI 调用一律过 `AiGatewayService` 并写 `ai_call_log`（scene=`photo_analysis`，provider=`qwen`）。
- 上传限制：jpg/jpeg/png/webp、单文件 ≤5MB（后端校验）、每菜谱 ≤50 张；multipart HTTP 上限 max-file-size 6MB / max-request-size 8MB；nginx `client_max_body_size 10m` 已满足不改。
- 图片 URL 返回相对路径（`/images/...`），小程序端拼 `BASE_URL`，Web 同源直用。
- 版本上限沿用 `RecipeService.MAX_VERSIONS=5`；PHOTO_ANALYSIS 新版本 change_note=advice 截 255。
- 既有 85 个测试必须始终全绿；后端命令：`cd server && mvn clean test`（本机需 Docker Desktop）。
- 前端代码不加注释（除非复刻现有注释风格）；Java 中文注释可保留项目现有风格。

---

### Task 1: Flyway V5 + RecipePhoto 实体 + 错误码 6xxx 段

**Files:**
- Create: `server/src/main/resources/db/migration/V5__recipe_photo.sql`
- Create: `server/src/main/java/com/linklife/recipe/entity/RecipePhoto.java`
- Create: `server/src/main/java/com/linklife/recipe/mapper/RecipePhotoMapper.java`
- Modify: `server/src/main/java/com/linklife/common/exception/ErrorCode.java`
- Modify: `docs/superpowers/specs/2026-09-18-p4-cooking-guide-design.md`（错误码表补 6007/6008）
- Test: `server/src/test/java/com/linklife/recipe/PhotoMigrationTest.java`

**Interfaces:**
- Produces: `RecipePhoto` 实体（字段 id, recipeId, stepNo, uploaderId, filePath, sizeBytes, analysis(String), analyzedAt(LocalDateTime), createdAt）；`RecipePhotoMapper extends BaseMapper<RecipePhoto>`；ErrorCode 枚举项 `FILE_TYPE_INVALID(6001)` `FILE_TOO_LARGE(6002)` `PHOTO_LIMIT_EXCEEDED(6003)` `PHOTO_NOT_FOUND(6004)` `VISION_AI_FAILED(6005)` `NOTHING_TO_APPLY(6006)` `PHOTO_NO_PERMISSION(6007)` `PHOTO_STEP_INVALID(6008)`

- [ ] **Step 1: 写 V5 迁移**

```sql
CREATE TABLE recipe_photo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    step_no INT NOT NULL COMMENT '菜谱步骤号，从 1 起',
    uploader_id BIGINT NOT NULL,
    file_path VARCHAR(255) NOT NULL COMMENT '相对路径 images/recipes/{recipeId}/{uuid}.{ext}',
    size_bytes BIGINT NOT NULL,
    analysis JSON NULL COMMENT '视觉分析结果 {advice, changes[]}',
    analyzed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_photo_recipe (recipe_id, step_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 写实体与 Mapper**

`RecipePhoto.java`（对齐 `RecipeVersion` 实体风格，`@TableName("recipe_photo")`，MyBatis-Plus 注解，字段用包装类型）：

```java
package com.linklife.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recipe_photo")
public class RecipePhoto {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private Integer stepNo;
    private Long uploaderId;
    private String filePath;
    private Long sizeBytes;
    private String analysis;
    private LocalDateTime analyzedAt;
    private LocalDateTime createdAt;
}
```

`RecipePhotoMapper.java`：

```java
package com.linklife.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linklife.recipe.entity.RecipePhoto;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecipePhotoMapper extends BaseMapper<RecipePhoto> {
}
```

- [ ] **Step 3: 加错误码**

在 `ErrorCode.java` 的 `PANTRY_ITEM_NOT_FOUND(5007, ...);` 后追加（把 5007 行末分号改逗号）：

```java
    PHOTO_NOT_FOUND(6004, "照片不存在", HttpStatus.NOT_FOUND),
    FILE_TYPE_INVALID(6001, "仅支持 jpg/png/webp 图片", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(6002, "图片不能超过 5MB", HttpStatus.BAD_REQUEST),
    PHOTO_LIMIT_EXCEEDED(6003, "该菜谱照片已达上限", HttpStatus.BAD_REQUEST),
    VISION_AI_FAILED(6005, "AI 视觉分析失败，请重试", HttpStatus.INTERNAL_SERVER_ERROR),
    NOTHING_TO_APPLY(6006, "无可应用的修改建议", HttpStatus.BAD_REQUEST),
    PHOTO_NO_PERMISSION(6007, "仅上传者或圈主可删除该照片", HttpStatus.FORBIDDEN),
    PHOTO_STEP_INVALID(6008, "步骤不存在", HttpStatus.BAD_REQUEST);
```

（按 6001→6008 顺序排放，注意 5007 行尾分号改逗号。）

- [ ] **Step 4: 同步 spec 错误码表**

在 spec §4 错误码表格末尾补两行：

```
| 6007 | PHOTO_NO_PERMISSION | 非上传者且非圈主删除照片 |
| 6008 | PHOTO_STEP_INVALID | 上传时 stepNo 不在当前版本步骤范围 |
```

- [ ] **Step 5: 写迁移测试**

`PhotoMigrationTest.java`：

```java
package com.linklife.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.linklife.IntegrationTestBase;
import com.linklife.recipe.entity.RecipePhoto;
import com.linklife.recipe.mapper.RecipePhotoMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PhotoMigrationTest extends IntegrationTestBase {

    @Autowired
    private RecipePhotoMapper photoMapper;

    @Test
    void photoTableRoundTrip() {
        RecipePhoto photo = new RecipePhoto();
        photo.setRecipeId(1L);
        photo.setStepNo(1);
        photo.setUploaderId(1L);
        photo.setFilePath("images/recipes/1/a.jpg");
        photo.setSizeBytes(1024L);
        photo.setCreatedAt(LocalDateTime.now());
        photoMapper.insert(photo);
        assertNotNull(photo.getId());

        RecipePhoto loaded = photoMapper.selectById(photo.getId());
        assertEquals("images/recipes/1/a.jpg", loaded.getFilePath());
    }
}
```

- [ ] **Step 6: 跑测试验证**

Run: `cd server && mvn test -Dtest=PhotoMigrationTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add server/src/main/resources/db/migration/V5__recipe_photo.sql \
  server/src/main/java/com/linklife/recipe/entity/RecipePhoto.java \
  server/src/main/java/com/linklife/recipe/mapper/RecipePhotoMapper.java \
  server/src/main/java/com/linklife/common/exception/ErrorCode.java \
  docs/superpowers/specs/2026-09-18-p4-cooking-guide-design.md \
  server/src/test/java/com/linklife/recipe/PhotoMigrationTest.java
git commit -m "feat(p4): recipe_photo 表 + 实体 + 6xxx 错误码段"
```

---

### Task 2: 双 ChatModel 基建 + AiGatewayService 视觉接口

**Files:**
- Modify: `server/pom.xml`（加 openai starter 依赖）
- Modify: `server/src/main/resources/application.yml`（AI 配置 + multipart）
- Create: `server/src/main/java/com/linklife/ai/gateway/AiClientConfig.java`
- Modify: `server/src/main/java/com/linklife/ai/gateway/AiGatewayService.java`（重构构造器 + 新增 callStructuredWithImage）
- Modify: `server/src/test/java/com/linklife/IntegrationTestBase.java`
- Modify: `server/src/test/java/com/linklife/ai/AiGatewayServiceTest.java`（FakeChatConfig 改造 + 新增视觉用例）
- Modify: `server/src/test/java/com/linklife/recipe/RecipeStreamTest.java`（FakeChatConfig 改造）

**Interfaces:**
- Produces: Spring bean `deepseekChatClient`（`ChatClient`，文本链路）与 `visionChatClient`（`ChatClient`，Qwen3-VL）；`AiGatewayService.callStructuredWithImage(Long userId, String scene, String prompt, byte[] imageBytes, String mimeType, Class<T> type) → T`（任何失败—including 解析失败—抛 `BusinessException(VISION_AI_FAILED)`；成功记 ai_call_log provider=qwen）

- [ ] **Step 1: pom 加依赖**

`server/pom.xml` 在 `spring-ai-starter-model-deepseek` 依赖后加：

```xml
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-model-openai</artifactId>
        </dependency>
```

- [ ] **Step 2: application.yml 配置**

`spring:` 下 `ai:` 段改为：

```yaml
  ai:
    chat:
      client:
        enabled: false
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:}
      chat:
        options:
          model: ${DEEPSEEK_MODEL:deepseek-chat}
    openai:
      api-key: ${DASHSCOPE_API_KEY:}
      base-url: ${DASHSCOPE_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
      chat:
        options:
          model: ${DASHSCOPE_VL_MODEL:qwen3-vl-flash}
```

`spring:` 下新增（与 datasource 平级）：

```yaml
  servlet:
    multipart:
      max-file-size: 6MB
      max-request-size: 8MB
```

文件末尾 `logging:` 前新增自定义配置段：

```yaml
link:
  images:
    base-dir: ${IMAGE_BASE_DIR:/data/images}
```

- [ ] **Step 3: 写 AiClientConfig**

```java
package com.linklife.ai.gateway;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiClientConfig {

    @Bean
    public ChatClient deepseekChatClient(
            org.springframework.ai.deepseek.DeepSeekChatModel model) {
        return ChatClient.create(model);
    }

    @Bean
    public ChatClient visionChatClient(OpenAiChatModel model) {
        return ChatClient.create(model);
    }
}
```

（若 `DeepSeekChatModel` 的包名在 1.0.0 中为 `org.springframework.ai.deepseek.DeepSeekChatModel` 之外的位置，以 IDE/maven 编译报错为准调整 import；openai 的 `OpenAiChatModel` 包名为 `org.springframework.ai.openai.OpenAiChatModel`。）

- [ ] **Step 4: 重构 AiGatewayService**

整类替换为（保留原 call/callStructured/stream/logFailure 逻辑不变，仅构造器来源变化并新增视觉方法）：

```java
package com.linklife.ai.gateway;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.recipe.dto.IterationResult;
import com.linklife.recipe.dto.RecipeContent;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class AiGatewayService {

    private final ChatClient chatClient;
    private final ChatClient visionChatClient;
    private final AiCallLogger aiCallLogger;
    private final String provider;
    private final String model;
    private final String visionProvider;
    private final String visionModel;

    public AiGatewayService(@Qualifier("deepseekChatClient") ChatClient chatClient,
                            @Qualifier("visionChatClient") ChatClient visionChatClient,
                            AiCallLogger aiCallLogger,
                            @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}") String model,
                            @Value("${spring.ai.openai.chat.options.model:qwen3-vl-flash}") String visionModel) {
        this.chatClient = chatClient;
        this.visionChatClient = visionChatClient;
        this.aiCallLogger = aiCallLogger;
        this.provider = "deepseek";
        this.model = model;
        this.visionProvider = "qwen";
        this.visionModel = visionModel;
    }

    public String call(Long userId, String scene, String prompt) {
        try {
            ChatResponse response = chatClient.prompt().user(prompt).call().chatResponse();
            Usage usage = response == null || response.getMetadata() == null
                    ? null : response.getMetadata().getUsage();
            Integer promptTokens = usage == null ? null : usage.getPromptTokens();
            Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
            aiCallLogger.log(userId, scene, provider, model, true, null,
                    promptTokens, completionTokens);
            return response == null ? "" : response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("ai call failed, scene={}", scene, e);
            aiCallLogger.log(userId, scene, provider, model, false,
                    truncate(e.getMessage()), null, null);
            throw e;
        }
    }

    /** 结构化输出：要求 prompt 已声明纯 JSON；解析失败抛 RECIPE_PARSE_FAILED。 */
    public <T> T callStructured(Long userId, String scene, String prompt, Class<T> type) {
        String raw = call(userId, scene, prompt);
        T result = AiResponseParser.parse(raw, type);
        if (result instanceof RecipeContent content) {
            content.validate();
        }
        if (result instanceof IterationResult iteration) {
            iteration.validate();
        }
        return result;
    }

    /** 多模态结构化输出（Qwen3-VL）：图片字节 + 提示词，任何失败统一抛 VISION_AI_FAILED。 */
    public <T> T callStructuredWithImage(Long userId, String scene, String prompt,
                                         byte[] imageBytes, String mimeType, Class<T> type) {
        String raw;
        try {
            Media media = new Media(MimeTypeUtils.parseMimeType(mimeType),
                    new ByteArrayResource(imageBytes));
            UserMessage message = new UserMessage(prompt, List.of(media));
            ChatResponse response = visionChatClient
                    .prompt(new Prompt(message)).call().chatResponse();
            Usage usage = response == null || response.getMetadata() == null
                    ? null : response.getMetadata().getUsage();
            Integer promptTokens = usage == null ? null : usage.getPromptTokens();
            Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
            aiCallLogger.log(userId, scene, visionProvider, visionModel, true, null,
                    promptTokens, completionTokens);
            raw = response == null ? "" : response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("ai vision call failed, scene={}", scene, e);
            aiCallLogger.log(userId, scene, visionProvider, visionModel, false,
                    truncate(e.getMessage()), null, null);
            throw new BusinessException(ErrorCode.VISION_AI_FAILED);
        }
        try {
            return AiResponseParser.parse(raw, type);
        } catch (BusinessException e) {
            // 解析失败归为视觉场景错误（6005），不复用 5004
            aiCallLogger.log(userId, scene, visionProvider, visionModel, false,
                    "PARSE_FAILED: " + truncate(raw), null, null);
            throw new BusinessException(ErrorCode.VISION_AI_FAILED);
        }
    }

    /** 流式增量文本；传输层成败在此记录（流式 usage 取不到记 null）。 */
    public Flux<String> stream(Long userId, String scene, String prompt) {
        return chatClient.prompt().user(prompt).stream().content()
                .doOnComplete(() ->
                        aiCallLogger.log(userId, scene, provider, model, true, null, null, null))
                .doOnError(e -> {
                    log.error("ai stream failed, scene={}", scene, e);
                    aiCallLogger.log(userId, scene, provider, model, false,
                            truncate(e.getMessage()), null, null);
                });
    }

    /** 供编排层记录解析/落库失败（传输成功但业务失败的场景）。 */
    public void logFailure(Long userId, String scene, String errorMsg) {
        aiCallLogger.log(userId, scene, provider, model, false, truncate(errorMsg), null, null);
    }

    private String truncate(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() > 512 ? msg.substring(0, 512) : msg;
    }
}
```

注意：`Media` 在 1.0.0 的包名为 `org.springframework.ai.content.Media`，若编译报错尝试 `org.springframework.ai.chat.messages.Media`（以编译为准）。

- [ ] **Step 5: 改 IntegrationTestBase**

`datasourceProps` 中追加两行（放在 deepseek key 之后）：

```java
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("link.images.base-dir", () -> {
            try {
                return java.nio.file.Files
                        .createTempDirectory("linklife-images").toString();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
```

- [ ] **Step 6: 改造 AiGatewayServiceTest**

`FakeChatConfig` 整体替换为（删除 ChatClient.Builder bean；callSpec/streamSpec 预取链路保持）：

```java
    @TestConfiguration
    static class FakeChatConfig {

        static ChatClient.CallResponseSpec callSpec;
        static ChatClient.StreamResponseSpec streamSpec;
        static ChatClient visionClient;

        @Bean
        @org.springframework.context.annotation.Primary
        ChatClient deepseekChatClient() {
            ChatClient client = Mockito.mock(ChatClient.class,
                    Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
            ChatClient.ChatClientRequestSpec requestSpec = client.prompt().user("prompt");
            callSpec = requestSpec.call();
            streamSpec = requestSpec.stream();
            return client;
        }

        @Bean
        @org.springframework.context.annotation.Primary
        ChatClient visionChatClient() {
            visionClient = Mockito.mock(ChatClient.class,
                    Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
            return visionClient;
        }
    }
```

类尾追加两个视觉用例（imports 补 `org.springframework.ai.chat.prompt.Prompt`）：

```java
    @Test
    void callStructuredWithImageParsesAndLogsQwenProvider() {
        doReturn(deepChatResponse(REPLY_JSON, 100, 50))
                .when(FakeChatConfig.visionClient)
                .prompt(Mockito.any(Prompt.class));

        RecipeContent content = aiGatewayService.callStructuredWithImage(
                42L, "photo_analysis", "prompt",
                new byte[]{1, 2, 3}, "image/jpeg", RecipeContent.class);

        assertEquals("鸡蛋", content.ingredients().get(0).name());
        verify(aiCallLogger).log(eq(42L), eq("photo_analysis"), anyString(), anyString(),
                eq(true), isNull(), eq(100), eq(50));
    }

    @Test
    void callStructuredWithImageWrapsFailuresAsVisionError() {
        doThrow(new RuntimeException("boom"))
                .when(FakeChatConfig.visionClient)
                .prompt(Mockito.any(Prompt.class));

        BusinessException e = assertThrows(BusinessException.class,
                () -> aiGatewayService.callStructuredWithImage(
                        42L, "photo_analysis", "prompt",
                        new byte[]{1}, "image/jpeg", RecipeContent.class));
        assertEquals(6005, e.getCode().code);
    }
```

注意：`BusinessException` 需要 `import com.linklife.common.exception.BusinessException;`；若 `BusinessException` 未暴露 `getCode()`，查看该类实际访问器（如 `errorCode`）并等价改写断言。

- [ ] **Step 7: 改造 RecipeStreamTest**

`FakeChatConfig` 替换为（该测试只用文本链路）：

```java
    @TestConfiguration
    static class FakeChatConfig {
        @Bean
        @org.springframework.context.annotation.Primary
        ChatClient deepseekChatClient() {
            return Mockito.mock(ChatClient.class,
                    Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        }
    }
```

同时把注入字段 `@Autowired private ChatClient.Builder chatClientBuilder;` 改为：

```java
    @Autowired
    private ChatClient chatClient;
```

并把 5 处 `when(chatClientBuilder.build().prompt().user(anyString()).stream().content())` 全部替换为 `when(chatClient.prompt().user(anyString()).stream().content())`。

- [ ] **Step 8: 全量测试回归**

Run: `cd server && mvn clean test`
Expected: 全部 PASS（85 个既有 + 新增 2 个视觉用例）。若出现 `ChatClient.Builder` 相关装配错误，检查 `spring.ai.chat.client.enabled=false` 是否生效、两个 @Primary bean 是否都被 TestConfiguration 提供。

- [ ] **Step 9: Commit**

```bash
git add server/pom.xml server/src/main/resources/application.yml \
  server/src/main/java/com/linklife/ai/gateway/AiClientConfig.java \
  server/src/main/java/com/linklife/ai/gateway/AiGatewayService.java \
  server/src/test/java/com/linklife/IntegrationTestBase.java \
  server/src/test/java/com/linklife/ai/AiGatewayServiceTest.java \
  server/src/test/java/com/linklife/recipe/RecipeStreamTest.java
git commit -m "feat(p4): 双 ChatModel 基建 + AiGatewayService 多模态接口(callStructuredWithImage)"
```

---

### Task 3: 图片存储 + 照片上传/列表/删除 API

**Files:**
- Create: `server/src/main/java/com/linklife/recipe/service/ImageStorageService.java`
- Create: `server/src/main/java/com/linklife/recipe/service/PhotoService.java`
- Create: `server/src/main/java/com/linklife/recipe/controller/PhotoController.java`
- Create: `server/src/main/java/com/linklife/recipe/dto/PhotoVO.java`
- Test: `server/src/test/java/com/linklife/recipe/PhotoApiTest.java`

**Interfaces:**
- Consumes: Task 1 的 `RecipePhotoMapper`/错误码；`RecipeService.requireVisibleRecipe(long userId, long recipeId)`（现有 public）；`order.mapper.DishMapper`；`circle.mapper.CircleMapper` + `circle.entity.Circle`（含 `getOwnerId()`）
- Produces: REST 端点 `POST /api/recipes/{id}/steps/{stepNo}/photos`（multipart 字段名 `file`，返回 `PhotoVO`）、`GET /api/recipes/{id}/photos`（返回 `List<PhotoVO>`，按 step_no,id 排序）、`DELETE /api/photos/{photoId}`；`PhotoService.requireVisiblePhoto(long userId, long photoId) → RecipePhoto`（Task 4/5 复用）；`ImageStorageService.save(long recipeId, String originalFilename, byte[] bytes) → String filePath`、`read(String filePath) → byte[]`、`delete(String filePath)`、`static contentType(String filePath) → String`
- `PhotoVO`: `record PhotoVO(Long id, Integer stepNo, String url, Long sizeBytes, Long uploaderId, boolean analyzed, LocalDateTime createdAt)`（url = `"/" + filePath`）

- [ ] **Step 1: 写 ImageStorageService**

```java
package com.linklife.recipe.service;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageStorageService {

    public static final long MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private final Path baseDir;

    public ImageStorageService(@Value("${link.images.base-dir:/data/images}") String baseDir) {
        this.baseDir = Paths.get(baseDir);
    }

    public String save(long recipeId, String originalFilename, byte[] bytes) {
        String ext = extension(originalFilename);
        if (ext == null || !ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_INVALID);
        }
        try {
            Path dir = baseDir.resolve("recipes").resolve(String.valueOf(recipeId));
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + "." + ext;
            Files.write(dir.resolve(name), bytes);
            return "images/recipes/" + recipeId + "/" + name;
        } catch (IOException e) {
            throw new UncheckedIOException("save image failed", e);
        }
    }

    public byte[] read(String filePath) {
        try {
            return Files.readAllBytes(baseDir.resolve(filePath));
        } catch (IOException e) {
            throw new UncheckedIOException("read image failed", e);
        }
    }

    public void delete(String filePath) {
        try {
            Files.deleteIfExists(baseDir.resolve(filePath));
        } catch (IOException e) {
            throw new UncheckedIOException("delete image failed", e);
        }
    }

    public static void validate(byte[] bytes, String originalFilename) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String ext = extension(originalFilename);
        if (ext == null || !ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_INVALID);
        }
        if (!magicOk(bytes, ext)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_INVALID);
        }
    }

    public static String contentType(String filePath) {
        String ext = extension(filePath);
        return ext == null ? "application/octet-stream" : CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    private static boolean magicOk(byte[] b, String ext) {
        if (b.length < 12) {
            return false;
        }
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        }
        if ("png".equals(ext)) {
            return (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        }
        if ("webp".equals(ext)) {
            return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        }
        return false;
    }

    private static String extension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase();
    }
}
```

- [ ] **Step 2: 写 PhotoService（上传/列表/删除部分）**

```java
package com.linklife.recipe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linklife.circle.entity.Circle;
import com.linklife.circle.mapper.CircleMapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.order.entity.Dish;
import com.linklife.order.mapper.DishMapper;
import com.linklife.recipe.dto.PhotoVO;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipePhoto;
import com.linklife.recipe.entity.RecipeVersion;
import com.linklife.recipe.mapper.RecipePhotoMapper;
import com.linklife.recipe.mapper.RecipeVersionMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PhotoService {

    public static final int MAX_PHOTOS_PER_RECIPE = 50;

    private final RecipePhotoMapper photoMapper;
    private final RecipeVersionMapper recipeVersionMapper;
    private final RecipeService recipeService;
    private final DishMapper dishMapper;
    private final CircleMapper circleMapper;
    private final ImageStorageService imageStorage;

    @Transactional
    public PhotoVO upload(long userId, long recipeId, int stepNo, MultipartFile file) {
        Recipe recipe = recipeService.requireVisibleRecipe(userId, recipeId);
        requireStepNo(recipe, stepNo);
        Long count = photoMapper.selectCount(new LambdaQueryWrapper<RecipePhoto>()
                .eq(RecipePhoto::getRecipeId, recipeId));
        if (count != null && count >= MAX_PHOTOS_PER_RECIPE) {
            throw new BusinessException(ErrorCode.PHOTO_LIMIT_EXCEEDED);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("read upload failed", e);
        }
        ImageStorageService.validate(bytes, file.getOriginalFilename());
        String filePath = imageStorage.save(recipeId, file.getOriginalFilename(), bytes);
        RecipePhoto photo = new RecipePhoto();
        photo.setRecipeId(recipeId);
        photo.setStepNo(stepNo);
        photo.setUploaderId(userId);
        photo.setFilePath(filePath);
        photo.setSizeBytes((long) bytes.length);
        photo.setCreatedAt(LocalDateTime.now());
        photoMapper.insert(photo);
        return toVO(photo);
    }

    public List<PhotoVO> list(long userId, long recipeId) {
        recipeService.requireVisibleRecipe(userId, recipeId);
        return photoMapper.selectList(new LambdaQueryWrapper<RecipePhoto>()
                        .eq(RecipePhoto::getRecipeId, recipeId)
                        .orderByAsc(RecipePhoto::getStepNo)
                        .orderByAsc(RecipePhoto::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public void delete(long userId, long photoId) {
        RecipePhoto photo = requireVisiblePhoto(userId, photoId);
        if (!Objects.equals(photo.getUploaderId(), userId)) {
            requireCircleOwner(userId, photo);
        }
        photoMapper.deleteById(photoId);
        imageStorage.delete(photo.getFilePath());
    }

    /** 供分析/应用复用：圈外用户按照片不存在处理。 */
    public RecipePhoto requireVisiblePhoto(long userId, long photoId) {
        RecipePhoto photo = photoMapper.selectById(photoId);
        if (photo == null) {
            throw new BusinessException(ErrorCode.PHOTO_NOT_FOUND);
        }
        recipeService.requireVisibleRecipe(userId, photo.getRecipeId());
        return photo;
    }

    private void requireStepNo(Recipe recipe, int stepNo) {
        RecipeVersion version = recipeVersionMapper.selectOne(
                new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipe.getId())
                        .eq(RecipeVersion::getVersion, recipe.getCurrentVersion()));
        if (version == null) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        boolean exists = RecipeService.parseContent(version.getContent()).steps().stream()
                .anyMatch(s -> s.no() != null && s.no() == stepNo);
        if (!exists) {
            throw new BusinessException(ErrorCode.PHOTO_STEP_INVALID);
        }
    }

    private void requireCircleOwner(long userId, RecipePhoto photo) {
        Recipe recipe = recipeService.requireVisibleRecipe(userId, photo.getRecipeId());
        Dish dish = dishMapper.selectById(recipe.getDishId());
        Circle circle = dish == null ? null : circleMapper.selectById(dish.getCircleId());
        if (circle == null || !Objects.equals(circle.getOwnerId(), userId)) {
            throw new BusinessException(ErrorCode.PHOTO_NO_PERMISSION);
        }
    }

    private PhotoVO toVO(RecipePhoto photo) {
        return new PhotoVO(photo.getId(), photo.getStepNo(), "/" + photo.getFilePath(),
                photo.getSizeBytes(), photo.getUploaderId(), photo.getAnalysis() != null,
                photo.getCreatedAt());
    }
}
```

- [ ] **Step 3: 写 PhotoController**

```java
package com.linklife.recipe.controller;

import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.recipe.dto.PhotoVO;
import com.linklife.recipe.service.PhotoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    @PostMapping("/api/recipes/{id}/steps/{stepNo}/photos")
    public Result<PhotoVO> upload(@PathVariable long id, @PathVariable int stepNo,
                                  @RequestParam("file") MultipartFile file) {
        return Result.ok(photoService.upload(UserContext.requireUserId(), id, stepNo, file));
    }

    @GetMapping("/api/recipes/{id}/photos")
    public Result<List<PhotoVO>> list(@PathVariable long id) {
        return Result.ok(photoService.list(UserContext.requireUserId(), id));
    }

    @DeleteMapping("/api/photos/{photoId}")
    public Result<Void> delete(@PathVariable long photoId) {
        photoService.delete(UserContext.requireUserId(), photoId);
        return Result.ok();
    }
}
```

- [ ] **Step 4: 写集成测试**

`PhotoApiTest.java` 完整内容如下（图片落盘目录由 Task 2 在 `IntegrationTestBase` 注入的 `link.images.base-dir` 随机临时目录提供，本测试无需额外注册；断言文件落盘用 `file_path` 的 URL 规则）：

```java
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
        String outsider = tokenOfMember("photo-outsider");
        mockMvc.perform(get("/api/recipes/" + recipeId + "/photos")
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(jsonPath("$.code").value(5001));
        mockMvc.perform(delete("/api/photos/" + photoId)
                        .header("Authorization", "Bearer " + outsider))
                .andExpect(jsonPath("$.code").value(5001));
    }
}
```

（圈外用户访问返回 5001，因为 `requireVisibleRecipe` 对圈外按"菜谱不存在"处理——照片随之不可见，属预期语义；上传者本人删除在 roundTrip 已覆盖。）

- [ ] **Step 5: 跑测试**

Run: `cd server && mvn test -Dtest=PhotoApiTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add server/src/main/java/com/linklife/recipe/service/ImageStorageService.java \
  server/src/main/java/com/linklife/recipe/service/PhotoService.java \
  server/src/main/java/com/linklife/recipe/controller/PhotoController.java \
  server/src/main/java/com/linklife/recipe/dto/PhotoVO.java \
  server/src/test/java/com/linklife/recipe/PhotoApiTest.java
git commit -m "feat(p4): 过程照片上传/列表/删除(本地卷存储+魔数校验)"
```

---

### Task 4: AI 视觉分析(analysis 端点 + 提示词模板)

**Files:**
- Create: `server/src/main/java/com/linklife/recipe/dto/PhotoAnalysis.java`
- Create: `server/src/main/java/com/linklife/recipe/service/PhotoAnalysisPrompts.java`
- Create: `server/src/main/java/com/linklife/recipe/service/PhotoAnalysisService.java`
- Modify: `server/src/main/java/com/linklife/recipe/service/PhotoService.java`（analysis 字段写库方法）
- Modify: `server/src/main/java/com/linklife/recipe/controller/PhotoController.java`（analysis 端点）
- Test: `server/src/test/java/com/linklife/recipe/PhotoAnalysisApiTest.java`

**Interfaces:**
- Consumes: `AiGatewayService.callStructuredWithImage(...)`（Task 2）；`PhotoService.requireVisiblePhoto(...)`、`ImageStorageService.read(...)`、`ImageStorageService.contentType(...)`；`RecipeService.get(userId, recipeId) → RecipeDetailVO`（record 访问器 `dishName()`/`content()`）；`TasteProfileService.getSummary(long userId) → String`（现有 public）
- Produces: `POST /api/photos/{photoId}/analysis` → `Result<PhotoAnalysis>`；`PhotoAnalysis(String advice, List<Change> changes)`，`Change(Integer stepNo, String text, Integer durationSec)`，方法 `validChanges()`；`PhotoAnalysisService.parseAnalysis(String json) → PhotoAnalysis`（Task 5 复用）
- `RecipeDetailVO` 若为 class 而非 record，访问器为 `getDishName()`/`getContent()`——实现时以实际类型为准。

- [ ] **Step 1: 写 PhotoAnalysis DTO**

```java
package com.linklife.recipe.dto;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import java.util.List;

public record PhotoAnalysis(String advice, List<Change> changes) {

    public record Change(Integer stepNo, String text, Integer durationSec) {
    }

    public void validate() {
        if (advice == null || advice.isBlank()) {
            throw new BusinessException(ErrorCode.VISION_AI_FAILED);
        }
    }

    public List<Change> validChanges() {
        return changes == null ? List.of() : changes;
    }
}
```

- [ ] **Step 2: 写提示词模板**

```java
package com.linklife.recipe.service;

public final class PhotoAnalysisPrompts {

    private PhotoAnalysisPrompts() {
    }

    public static String build(String dishName, String stepText, String tasteSummary) {
        String taste = tasteSummary == null || tasteSummary.isBlank() ? "无记录" : tasteSummary;
        return "你是烹饪助手。用户正在做「" + dishName + "」，当前步骤：" + stepText
                + "。用户口味偏好：" + taste + "。"
                + "请分析这张烹饪过程照片（调料用量、火候、色泽、操作手法是否正确），给出简短建议；"
                + "仅当照片暴露出与菜谱步骤直接相关的问题时，输出需要修改的步骤（只改受影响步骤的文本或时长）。\n"
                + "仅输出 JSON，格式：{\"advice\":\"50字内建议\","
                + "\"changes\":[{\"stepNo\":1,\"text\":\"修改后的步骤文本\",\"durationSec\":60}]}；"
                + "无需修改时 changes 为空数组 []；不要输出 JSON 以外的任何内容。";
    }
}
```

- [ ] **Step 3: 写 PhotoAnalysisService**

```java
package com.linklife.recipe.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.recipe.dto.PhotoAnalysis;
import com.linklife.recipe.dto.RecipeContent;
import com.linklife.recipe.dto.RecipeDetailVO;
import com.linklife.recipe.entity.RecipePhoto;
import com.linklife.recipe.mapper.RecipePhotoMapper;
import com.linklife.user.service.TasteProfileService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoAnalysisService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final PhotoService photoService;
    private final RecipeService recipeService;
    private final TasteProfileService tasteProfileService;
    private final ImageStorageService imageStorage;
    private final AiGatewayService aiGatewayService;
    private final RecipePhotoMapper photoMapper;

    public PhotoAnalysis analyze(long userId, long photoId) {
        RecipePhoto photo = photoService.requireVisiblePhoto(userId, photoId);
        RecipeDetailVO detail = recipeService.get(userId, photo.getRecipeId());
        String stepText = detail.content().steps().stream()
                .filter(s -> s.no() != null && s.no().equals(photo.getStepNo()))
                .map(RecipeContent.Step::text)
                .findFirst()
                .orElse("");
        String taste = tasteProfileService.getSummary(userId);
        String prompt = PhotoAnalysisPrompts.build(detail.dishName(), stepText, taste);
        byte[] bytes = imageStorage.read(photo.getFilePath());
        PhotoAnalysis result = aiGatewayService.callStructuredWithImage(
                userId, "photo_analysis", prompt, bytes,
                ImageStorageService.contentType(photo.getFilePath()), PhotoAnalysis.class);
        result.validate();
        photo.setAnalysis(toJson(result));
        photo.setAnalyzedAt(LocalDateTime.now());
        photoMapper.updateById(photo);
        return result;
    }

    public static PhotoAnalysis parseAnalysis(String json) {
        try {
            return MAPPER.readValue(json, PhotoAnalysis.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.NOTHING_TO_APPLY);
        }
    }

    public static String toJson(PhotoAnalysis analysis) {
        try {
            return MAPPER.writeValueAsString(analysis);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VISION_AI_FAILED);
        }
    }
}
```

（若 `RecipeDetailVO` 为 class，`detail.content()`/`detail.dishName()` 相应改为 `getContent()`/`getDishName()`。）

- [ ] **Step 4: 加 analysis 端点**

`PhotoController` 加字段 `private final PhotoAnalysisService photoAnalysisService;` 与方法：

```java
    @PostMapping("/api/photos/{photoId}/analysis")
    public Result<PhotoAnalysis> analyze(@PathVariable long photoId) {
        return Result.ok(photoAnalysisService.analyze(UserContext.requireUserId(), photoId));
    }
```

import 补 `com.linklife.recipe.dto.PhotoAnalysis`。

- [ ] **Step 5: 写集成测试**

`PhotoAnalysisApiTest.java` 完整内容如下（seed = 建圈/菜谱/版本 + **真实 multipart 上传一张照片**（保证 file 可读）+ mock vision ChatClient）：

```java
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
        Mockito.when(visionClient.prompt(Mockito.any(Prompt.class)).call().chatResponse())
                .thenReturn(textResponse(VISION_JSON));

        mockMvc.perform(post("/api/photos/" + photoId + "/analysis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.advice").value("盐放多了，建议减半"))
                .andExpect(jsonPath("$.data.changes[0].stepNo").value(1));
    }

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
        Mockito.when(visionClient.prompt(Mockito.any(Prompt.class)).call().chatResponse())
                .thenReturn(textResponse("这不是 JSON"));

        mockMvc.perform(post("/api/photos/" + photoId + "/analysis")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(6005));
    }
}
```

（mock 的 vision ChatClient 在 `analyze` 全链路之前拦截调用，`ImageStorageService.read` 读的是真实上传的临时文件，可正常落盘读取。）

- [ ] **Step 6: 跑测试**

Run: `cd server && mvn test -Dtest=PhotoAnalysisApiTest`
Expected: PASS（3 用例）

- [ ] **Step 7: 全量回归 + Commit**

Run: `cd server && mvn clean test` → 全绿

```bash
git add server/src/main/java/com/linklife/recipe/dto/PhotoAnalysis.java \
  server/src/main/java/com/linklife/recipe/service/PhotoAnalysisPrompts.java \
  server/src/main/java/com/linklife/recipe/service/PhotoAnalysisService.java \
  server/src/main/java/com/linklife/recipe/controller/PhotoController.java \
  server/src/test/java/com/linklife/recipe/PhotoAnalysisApiTest.java
git commit -m "feat(p4): Qwen3-VL 视觉分析接口 + 提示词模板"
```

---

### Task 5: 补丁应用(apply 端点 → PHOTO_ANALYSIS 新版本)

**Files:**
- Modify: `server/src/main/java/com/linklife/recipe/service/RecipeService.java`（新增 applyPhotoPatch）
- Modify: `server/src/main/java/com/linklife/recipe/service/PhotoService.java`（新增 applyAnalysis）
- Modify: `server/src/main/java/com/linklife/recipe/controller/PhotoController.java`（apply 端点）
- Test: `server/src/test/java/com/linklife/recipe/PhotoApplyApiTest.java`

**Interfaces:**
- Consumes: Task 4 的 `PhotoAnalysisService.parseAnalysis(...)`、`PhotoAnalysis.validChanges()`；`PhotoService.requireVisiblePhoto(...)`；`RecipeService` 内部 `insertVersion`/`parseContent`/`versionCount`
- Produces: `POST /api/photos/{photoId}/apply` → `Result<Integer>`（新版本号）；`RecipeService.applyPhotoPatch(long userId, long recipeId, List<PhotoAnalysis.Change> changes, String changeNote) → int`

- [ ] **Step 1: RecipeService.applyPhotoPatch**

`RecipeService` 加 import（`PhotoAnalysis`、`Collectors`、`ArrayList` 等）并新增方法（放在 `saveIterated` 后）：

```java
    @Transactional
    public int applyPhotoPatch(long userId, long recipeId, List<PhotoAnalysis.Change> changes,
                               String changeNote) {
        Recipe recipe = requireVisibleRecipe(userId, recipeId);
        if (versionCount(recipeId) >= MAX_VERSIONS) {
            throw new BusinessException(ErrorCode.RECIPE_VERSION_LIMIT);
        }
        RecipeVersion current = requireVersionRow(recipeId, recipe.getCurrentVersion());
        RecipeContent content = parseContent(current.getContent());
        List<RecipeContent.Step> newSteps = new ArrayList<>();
        boolean changed = false;
        for (RecipeContent.Step step : content.steps()) {
            RecipeContent.Step merged = step;
            for (PhotoAnalysis.Change c : changes) {
                if (c.stepNo() != null && c.stepNo().equals(step.no())) {
                    String text = c.text() == null || c.text().isBlank() ? step.text() : c.text();
                    Integer dur = c.durationSec() == null ? step.durationSec() : c.durationSec();
                    merged = new RecipeContent.Step(step.no(), text, dur);
                    changed = true;
                }
            }
            newSteps.add(merged);
        }
        if (!changed) {
            throw new BusinessException(ErrorCode.NOTHING_TO_APPLY);
        }
        RecipeContent result = new RecipeContent(content.servings(), content.totalMinutes(),
                content.ingredients(), content.seasonings(), newSteps, content.tips());
        int next = versionCount(recipeId) + 1;
        insertVersion(recipeId, next, "PHOTO_ANALYSIS", result,
                changeNote != null && changeNote.length() > 255
                        ? changeNote.substring(0, 255) : changeNote, userId);
        recipe.setCurrentVersion(next);
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.updateById(recipe);
        return next;
    }
```

同时给 `RecipeVersion.source` 注释更新：`V4__recipe.sql` 不改（已迁移），在实体或 DAO 层无枚举校验，无需额外改动。

- [ ] **Step 2: PhotoService.applyAnalysis**

`PhotoService` 加依赖 `private final PhotoAnalysisService photoAnalysisService;`（会形成 PhotoService↔PhotoAnalysisService 循环依赖——PhotoAnalysisService 已注入 PhotoService。**解法**：applyAnalysis 放到 PhotoAnalysisService 里而不是 PhotoService）：

在 `PhotoAnalysisService` 中新增：

```java
    @org.springframework.transaction.annotation.Transactional
    public int apply(long userId, long photoId) {
        RecipePhoto photo = photoService.requireVisiblePhoto(userId, photoId);
        if (photo.getAnalysis() == null) {
            throw new BusinessException(ErrorCode.NOTHING_TO_APPLY);
        }
        PhotoAnalysis analysis = parseAnalysis(photo.getAnalysis());
        return recipeService.applyPhotoPatch(userId, photo.getRecipeId(),
                analysis.validChanges(), analysis.advice());
    }
```

- [ ] **Step 3: apply 端点**

`PhotoController` 加：

```java
    @PostMapping("/api/photos/{photoId}/apply")
    public Result<Integer> apply(@PathVariable long photoId) {
        return Result.ok(photoAnalysisService.apply(UserContext.requireUserId(), photoId));
    }
```

- [ ] **Step 4: 写集成测试**

`PhotoApplyApiTest.java`（seed 照抄 PhotoAnalysisApiTest 的 FakeVisionConfig + seed，但**不 mock vision**，直接手插一条带 analysis 的照片行）：

```java
package com.linklife.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
`PhotoApplyApiTest.java` 完整内容如下（**不 mock vision**；直接手插一条带 analysis JSON 的照片行，验证 apply 链路）：

```java
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
        org.junit.jupiter.api.Assertions.assertTrue(v2.getContent().contains("放盐半勺"));
        org.junit.jupiter.api.Assertions.assertTrue(v2.getContent().contains("\"durationSec\":30"));
    }

    @Test
    void applyWithoutAnalysisReturns6006() throws Exception {
        RecipePhoto photo = photoMapper.selectById(photoId);
        photo.setAnalysis(null);
        photoMapper.updateById(photo);
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
```

- [ ] **Step 5: 跑测试**

Run: `cd server && mvn test -Dtest=PhotoApplyApiTest`
Expected: PASS

- [ ] **Step 6: 全量回归 + Commit**

Run: `cd server && mvn clean test` → 全绿

```bash
git add server/src/main/java/com/linklife/recipe/service/RecipeService.java \
  server/src/main/java/com/linklife/recipe/service/PhotoAnalysisService.java \
  server/src/main/java/com/linklife/recipe/controller/PhotoController.java \
  server/src/test/java/com/linklife/recipe/PhotoApplyApiTest.java
git commit -m "feat(p4): 视觉分析补丁确认应用→PHOTO_ANALYSIS 新版本"
```

---

### Task 6: 小程序烹饪模式页 + 上传封装

**Files:**
- Modify: `miniapp/utils/request.js`（导出 refresh）
- Create: `miniapp/utils/upload.js`
- Create: `miniapp/assets/ding.wav`（脚本生成）
- Create: `miniapp/pages/cook-mode/cook-mode.js` / `.wxml` / `.wxss` / `.json`
- Modify: `miniapp/app.json`（注册页面）
- Modify: `miniapp/pages/recipe-detail/recipe-detail.js`（入口 + durationSec 修复 + SOURCE_TEXT）
- Modify: `miniapp/pages/recipe-detail/recipe-detail.wxml`（开始烹饪按钮 + hint 文案）

**Interfaces:**
- Consumes: `request(path, options)`（现有）、`upload(path, filePath)`（本任务新增，上传成功 resolve data，401 自动刷新重试）
- Produces: 页面路由 `pages/cook-mode/cook-mode?id={recipeId}`；`utils/upload.js` 导出 `{ upload }`；`utils/request.js` 额外导出 `refresh`

- [ ] **Step 1: request.js 导出 refresh**

末行 `module.exports = { request, BASE_URL };` 改为 `module.exports = { request, BASE_URL, refresh };`

- [ ] **Step 2: 写 utils/upload.js**

```js
const { BASE_URL, refresh } = require('./request');

function rawUpload(path, filePath, token) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: BASE_URL + path,
      filePath,
      name: 'file',
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success: (res) => {
        let body;
        try {
          body = JSON.parse(res.data);
        } catch (e) {
          reject({ message: '上传失败' });
          return;
        }
        if (body.code === 0) resolve(body.data);
        else reject(body);
      },
      fail: () => reject({ message: '上传失败，请检查网络' }),
    });
  });
}

async function upload(path, filePath) {
  const token = wx.getStorageSync('accessToken');
  try {
    return await rawUpload(path, filePath, token);
  } catch (err) {
    if (err && err.code === 2002) {
      let newToken;
      try {
        newToken = await refresh();
      } catch (refreshErr) {
        wx.clearStorageSync();
        wx.reLaunch({ url: '/pages/login/login' });
        throw refreshErr;
      }
      return rawUpload(path, filePath, newToken);
    }
    throw err;
  }
}

module.exports = { upload };
```

- [ ] **Step 3: 生成提示音**

Run:

```bash
mkdir -p miniapp/assets && python3 - <<'EOF'
import wave, math, struct
sr = 44100
samples = []
for freq in (880.0, 1174.7):
    for i in range(int(sr * 0.28)):
        t = i / sr
        samples.append(0.6 * math.exp(-6 * t) * math.sin(2 * math.pi * freq * t))
    samples += [0.0] * int(sr * 0.06)
with wave.open('miniapp/assets/ding.wav', 'wb') as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(sr)
    w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, s)) * 32767)) for s in samples))
EOF
```

Expected: `miniapp/assets/ding.wav` 生成（双音"叮咚"）。

- [ ] **Step 4: 写 cook-mode 页面**

`cook-mode.json`：

```json
{
  "navigationBarTitleText": "烹饪模式"
}
```

`cook-mode.js`：

```js
const { request, BASE_URL } = require('../../utils/request');
const { upload } = require('../../utils/upload');

Page({
  data: {
    recipe: null,
    steps: [],
    current: 0,
    remainText: '00:00',
    progressPct: 0,
    counting: false,
    hasDuration: false,
    currentPhotos: [],
    advice: null,
    advicePhotoId: null,
  },

  onLoad(options) {
    this.recipeId = options.id;
    this.timer = null;
    this.remainSec = 0;
    this.audio = null;
    this.load();
  },

  onShow() {
    wx.setKeepScreenOn({ keepScreenOn: true });
  },

  onHide() {
    wx.setKeepScreenOn({ keepScreenOn: false });
    this.stopTimer();
  },

  onUnload() {
    wx.setKeepScreenOn({ keepScreenOn: false });
    this.stopTimer();
    if (this.audio) this.audio.destroy();
  },

  load() {
    const self = this;
    request('/api/recipes/' + this.recipeId).then((recipe) => {
      const steps = (recipe.content.steps || []).map((s) => ({
        no: s.no,
        text: s.text,
        durationSec: s.durationSec || 0,
      }));
      self.setData({ recipe, steps });
      self.enterStep(self.data.current);
      return request('/api/recipes/' + this.recipeId + '/photos');
    }).then((photos) => {
      const byStep = {};
      (photos || []).forEach((p) => {
        p.fullUrl = p.url.startsWith('http') ? p.url : BASE_URL + p.url;
        (byStep[p.stepNo] = byStep[p.stepNo] || []).push(p);
      });
      this.photosByStep = byStep;
      self.refreshPhotos();
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' });
    });
  },

  refreshPhotos() {
    const step = this.data.steps[this.data.current];
    const list = (step && this.photosByStep[step.no]) || [];
    this.setData({ currentPhotos: list });
  },

  enterStep(idx) {
    this.stopTimer();
    const step = this.data.steps[idx];
    const duration = step ? step.durationSec : 0;
    this.setData({
      current: idx,
      remainText: this.fmt(duration),
      progressPct: 0,
      counting: false,
      hasDuration: duration > 0,
    });
    this.remainSec = duration;
    this.refreshPhotos();
    if (duration > 0) this.startTimer();
  },

  fmt(sec) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return (m < 10 ? '0' + m : m) + ':' + (s < 10 ? '0' + s : s);
  },

  startTimer() {
    if (this.timer || this.remainSec <= 0) return;
    const self = this;
    this.setData({ counting: true });
    this.timer = setInterval(() => {
      self.remainSec = Math.max(0, self.remainSec - 1);
      const total = self.data.steps[self.data.current].durationSec || 1;
      self.setData({
        remainText: self.fmt(self.remainSec),
        progressPct: Math.round(((total - self.remainSec) / total) * 100),
      });
      if (self.remainSec <= 0) self.finishStep();
    }, 1000);
  },

  stopTimer() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.data.counting) this.setData({ counting: false });
  },

  finishStep() {
    this.stopTimer();
    this.playDing();
    wx.vibrateLong({ fail: () => {} });
  },

  playDing() {
    try {
      if (!this.audio) {
        this.audio = wx.createInnerAudioContext();
        this.audio.src = '/assets/ding.wav';
      }
      this.audio.stop();
      this.audio.play();
    } catch (e) {
      wx.vibrateLong({ fail: () => {} });
    }
  },

  toggleTimer() {
    if (this.data.counting) this.stopTimer();
    else this.startTimer();
  },

  skipTimer() {
    this.remainSec = 0;
    this.setData({ remainText: this.fmt(0), progressPct: 100 });
    this.stopTimer();
  },

  prevStep() {
    if (this.data.current > 0) this.enterStep(this.data.current - 1);
  },

  nextStep() {
    if (this.data.current < this.data.steps.length - 1) {
      this.enterStep(this.data.current + 1);
    }
  },

  takePhoto() {
    const self = this;
    const stepNo = this.data.steps[this.data.current].no;
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sourceType: ['camera', 'album'],
      sizeType: ['compressed'],
      success(res) {
        const filePath = res.tempFiles[0].tempFilePath;
        wx.showLoading({ title: '上传中' });
        upload('/api/recipes/' + self.recipeId + '/steps/' + stepNo + '/photos', filePath)
          .then(() => {
            wx.hideLoading();
            self.load();
          })
          .catch((err) => {
            wx.hideLoading();
            wx.showToast({ title: (err && err.message) || '上传失败', icon: 'none' });
          });
      },
    });
  },

  askAi(e) {
    const self = this;
    const photoId = e.currentTarget.dataset.id;
    wx.showLoading({ title: 'AI 分析中' });
    request('/api/photos/' + photoId + '/analysis', { method: 'POST' })
      .then((result) => {
        wx.hideLoading();
        self.setData({ advice: result, advicePhotoId: photoId });
      })
      .catch((err) => {
        wx.hideLoading();
        wx.showToast({ title: (err && err.message) || '分析失败', icon: 'none' });
      });
  },

  dismissAdvice() {
    this.setData({ advice: null, advicePhotoId: null });
  },

  applyAdvice() {
    const self = this;
    request('/api/photos/' + this.data.advicePhotoId + '/apply', { method: 'POST' })
      .then(() => {
        wx.showToast({ title: '已生成新版本', icon: 'success' });
        self.setData({ advice: null, advicePhotoId: null });
        self.load();
      })
      .catch((err) => {
        wx.showToast({ title: (err && err.message) || '应用失败', icon: 'none' });
      });
  },
});
```

`cook-mode.wxml`：

```xml
<view class="cook" wx:if="{{recipe}}">
  <view class="progress-line">
    <view class="progress-inner" style="width: {{progressPct}}%"></view>
  </view>
  <view class="head">步骤 {{steps[current].no}} / {{steps.length}}</view>

  <view class="step-text">{{steps[current].text}}</view>

  <view class="timer" wx:if="{{hasDuration}}">
    <view class="remain">{{remainText}}</view>
    <view class="timer-btns">
      <button size="mini" bindtap="toggleTimer">{{counting ? '暂停' : '继续'}}</button>
      <button size="mini" bindtap="skipTimer">跳过</button>
    </view>
  </view>
  <view class="no-timer" wx:else>本步骤无需计时</view>

  <view class="nav">
    <button size="mini" plain bindtap="prevStep" disabled="{{current === 0}}">上一步</button>
    <button size="mini" bindtap="takePhoto">拍照</button>
    <button size="mini" plain bindtap="nextStep"
            disabled="{{current === steps.length - 1}}">下一步</button>
  </view>

  <view class="photos" wx:if="{{currentPhotos.length}}">
    <view class="photo" wx:for="{{currentPhotos}}" wx:key="id">
      <image src="{{item.fullUrl}}" mode="aspectFill" />
      <view class="photo-ops">
        <text class="ai-btn" bindtap="askAi" data-id="{{item.id}}">{{item.analyzed ? '重新分析' : '问 AI'}}</text>
      </view>
    </view>
  </view>

  <view class="advice" wx:if="{{advice}}">
    <view class="advice-title">AI 建议</view>
    <view class="advice-body">{{advice.advice}}</view>
    <view class="advice-change" wx:for="{{advice.changes}}" wx:key="index">
      步骤 {{item.stepNo}}：{{item.text || '仅调整时长'}}
    </view>
    <view class="advice-btns">
      <button size="mini" type="primary" bindtap="applyAdvice">应用修改</button>
      <button size="mini" bindtap="dismissAdvice">忽略</button>
    </view>
  </view>
</view>
<view wx:else class="cook"><text>加载中…</text></view>
```

`cook-mode.wxss`：

```css
.cook { padding: 24rpx; min-height: 100vh; box-sizing: border-box; background: #111; color: #fff; }
.progress-line { height: 8rpx; background: #333; border-radius: 4rpx; overflow: hidden; }
.progress-inner { height: 100%; background: #07c160; transition: width .5s; }
.head { text-align: center; margin: 24rpx 0; color: #aaa; }
.step-text { font-size: 40rpx; line-height: 1.6; margin: 48rpx 0; }
.timer { text-align: center; margin: 32rpx 0; }
.remain { font-size: 120rpx; font-weight: 700; color: #07c160; }
.timer-btns, .nav { display: flex; justify-content: center; gap: 24rpx; margin-top: 24rpx; }
.no-timer { text-align: center; color: #666; margin: 32rpx 0; }
.photos { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 32rpx; }
.photo { width: 200rpx; }
.photo image { width: 200rpx; height: 200rpx; border-radius: 12rpx; }
.photo-ops { text-align: center; margin-top: 8rpx; }
.ai-btn { color: #07c160; font-size: 24rpx; }
.advice { background: #1e1e1e; border-radius: 16rpx; padding: 24rpx; margin-top: 32rpx; }
.advice-title { color: #07c160; font-weight: 700; margin-bottom: 12rpx; }
.advice-change { color: #ccc; font-size: 26rpx; margin-top: 8rpx; }
.advice-btns { display: flex; gap: 24rpx; margin-top: 20rpx; }
```

- [ ] **Step 5: app.json 注册 + 详情页入口**

`app.json` pages 数组在 `"pages/pantry/pantry"` 后加 `"pages/cook-mode/cook-mode"`。

`recipe-detail.js`：
1. `SOURCE_TEXT` 加 `PHOTO_ANALYSIS: '拍照修正'`。
2. `saveEdit()` 的 steps 构造改为保留 durationSec：

```js
    const steps = String(f.stepsText || '')
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
      .map((text, idx) => {
        const orig = (this.data.recipe.content.steps || [])[idx];
        return {
          no: idx + 1,
          text,
          ...(orig && orig.durationSec ? { durationSec: orig.durationSec } : {}),
        };
      });
```

3. 新增方法：

```js
  startCook() {
    wx.navigateTo({
      url: '/pages/cook-mode/cook-mode?id=' + this.recipeId,
    });
  },
```

`recipe-detail.wxml`：第一张卡片（第 6 行按钮旁）加 `<button size="mini" type="primary" bindtap="startCook">开始烹饪</button>`；第 26 行 hint 文案改为 `每行一步，按行顺序编号（保留各步骤时长）`。

- [ ] **Step 6: 验证与 Commit**

检查（无自动化测试，人工核对）：`node -e "require('./miniapp/utils/upload.js')"` 不可用（wx 依赖），改为静态检查语法 `node --check miniapp/utils/upload.js`（会因 wx 未定义？`--check` 只查语法不执行，可通过）。4 个新文件 + 2 个修改文件齐全。

```bash
git add miniapp/utils/request.js miniapp/utils/upload.js miniapp/assets/ding.wav \
  miniapp/pages/cook-mode miniapp/app.json \
  miniapp/pages/recipe-detail/recipe-detail.js miniapp/pages/recipe-detail/recipe-detail.wxml
git commit -m "feat(p4): 小程序烹饪模式页(计时/亮屏常亮/拍照/AI 分析)+上传封装"
```

---

### Task 7: Web 端 CookMode 页 + 照片展示/上传

**Files:**
- Modify: `web/src/api/recipe.js`
- Create: `web/src/views/CookMode.vue`
- Modify: `web/src/router.js`
- Modify: `web/src/views/RecipeDetail.vue`

**Interfaces:**
- Consumes: Task 3/4/5 的 REST API；`request`（现有 axios 封装）
- Produces: 路由 `/recipes/:id/cook`；api 函数 `listPhotos(id)` `deletePhoto(photoId)` `analyzePhoto(photoId)` `applyPhoto(photoId)` `uploadPhoto(id, stepNo, file)`

- [ ] **Step 1: api/recipe.js 扩展**

末尾追加：

```js
export const listPhotos = (id) => request('/api/recipes/' + id + '/photos')
export const deletePhoto = (photoId) =>
  request('/api/photos/' + photoId, { method: 'DELETE' })
export const analyzePhoto = (photoId) =>
  request('/api/photos/' + photoId + '/analysis', { method: 'POST' })
export const applyPhoto = (photoId) =>
  request('/api/photos/' + photoId + '/apply', { method: 'POST' })
export async function uploadPhoto(id, stepNo, file) {
  const fd = new FormData()
  fd.append('file', file)
  const res = await fetch(`/api/recipes/${id}/steps/${stepNo}/photos`, {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + localStorage.getItem('accessToken') },
    body: fd,
  })
  const body = await res.json()
  if (body.code === 0) return body.data
  throw body
}
```

（用 fetch 而非 axios 免改 request.js；401 刷新边缘场景真机验证阶段确认。）

- [ ] **Step 2: CookMode.vue**

```vue
<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getRecipe, listPhotos, analyzePhoto, applyPhoto, uploadPhoto, deletePhoto } from '../api/recipe'
import { showToast } from '../utils/toast'

const route = useRoute()
const id = route.params.id
const recipe = ref(null)
const steps = ref([])
const current = ref(0)
const remainSec = ref(0)
const counting = ref(false)
const photosByStep = ref({})
const advice = ref(null)
const advicePhotoId = ref(null)

let timer = null
let audioCtx = null

const step = computed(() => steps.value[current.value] || {})
const hasDuration = computed(() => (step.value.durationSec || 0) > 0)
const remainText = computed(() => {
  const m = Math.floor(remainSec.value / 60)
  const s = remainSec.value % 60
  return `${m < 10 ? '0' + m : m}:${s < 10 ? '0' + s : s}`
})
const progressPct = computed(() => {
  const total = step.value.durationSec || 1
  return Math.round(((total - remainSec.value) / total) * 100)
})

function beep() {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)()
    const osc = audioCtx.createOscillator()
    const gain = audioCtx.createGain()
    osc.connect(gain)
    gain.connect(audioCtx.destination)
    osc.frequency.value = 880
    gain.gain.setValueAtTime(0.4, audioCtx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.8)
    osc.start()
    osc.stop(audioCtx.currentTime + 0.8)
  } catch (e) { /* 音频不可用则静默 */ }
}

function stopTimer() {
  if (timer) { clearInterval(timer); timer = null }
  counting.value = false
}

function startTimer() {
  if (timer || remainSec.value <= 0) return
  counting.value = true
  timer = setInterval(() => {
    remainSec.value = Math.max(0, remainSec.value - 1)
    if (remainSec.value === 0) { stopTimer(); beep() }
  }, 1000)
}

function enterStep(idx) {
  stopTimer()
  current.value = idx
  remainSec.value = step.value.durationSec || 0
  if (remainSec.value > 0) startTimer()
}

function toggleTimer() { counting.value ? stopTimer() : startTimer() }
function skipTimer() { stopTimer(); remainSec.value = 0 }
function prevStep() { if (current.value > 0) enterStep(current.value - 1) }
function nextStep() { if (current.value < steps.value.length - 1) enterStep(current.value + 1) }

async function load() {
  try {
    recipe.value = await getRecipe(id)
    steps.value = (recipe.value.content.steps || []).map((s) => ({ ...s }))
    remainSec.value = steps.value[0]?.durationSec || 0
    if (remainSec.value > 0) startTimer()
    const photos = await listPhotos(id)
    const byStep = {}
    photos.forEach((p) => { (byStep[p.stepNo] = byStep[p.stepNo] || []).push(p) })
    photosByStep.value = byStep
  } catch (e) { showToast(e.message || '加载失败') }
}
load()

async function onFileChange(e, stepNo) {
  const file = e.target.files[0]
  if (!file) return
  try {
    await uploadPhoto(id, stepNo, file)
    showToast('已上传')
    await load()
  } catch (err) { showToast(err.message || '上传失败') }
  e.target.value = ''
}

async function askAi(photoId) {
  try {
    advice.value = await analyzePhoto(photoId)
    advicePhotoId.value = photoId
  } catch (e) { showToast(e.message || '分析失败') }
}

async function doApply() {
  try {
    await applyPhoto(advicePhotoId.value)
    showToast('已生成新版本')
    advice.value = null
    advicePhotoId.value = null
    await load()
  } catch (e) { showToast(e.message || '应用失败') }
}

async function doDelete(photoId) {
  try {
    await deletePhoto(photoId)
    await load()
  } catch (e) { showToast(e.message || '删除失败') }
}

onBeforeUnmount(stopTimer)
</script>

<template>
  <div class="cook" v-if="recipe">
    <div class="bar"><div class="bar-inner" :style="{ width: progressPct + '%' }"></div></div>
    <p class="head">步骤 {{ step.no }} / {{ steps.length }}</p>
    <p class="step-text">{{ step.text }}</p>
    <div v-if="hasDuration" class="timer">
      <p class="remain">{{ remainText }}</p>
      <button @click="toggleTimer">{{ counting ? '暂停' : '继续' }}</button>
      <button @click="skipTimer">跳过</button>
    </div>
    <p v-else class="meta">本步骤无需计时</p>
    <div class="nav">
      <button :disabled="current === 0" @click="prevStep">上一步</button>
      <label class="upload-btn">
        拍照
        <input type="file" accept="image/*" capture="environment" hidden
               @change="onFileChange($event, step.no)" />
      </label>
      <button :disabled="current === steps.length - 1" @click="nextStep">下一步</button>
    </div>

    <div class="photos" v-if="(photosByStep[step.no] || []).length">
      <div class="photo" v-for="p in photosByStep[step.no]" :key="p.id">
        <img :src="p.url" />
        <a href="#" @click.prevent="askAi(p.id)">{{ p.analyzed ? '重新分析' : '问 AI' }}</a>
        <a href="#" class="del" @click.prevent="doDelete(p.id)">删除</a>
      </div>
    </div>

    <div class="advice" v-if="advice">
      <h3>AI 建议</h3>
      <p>{{ advice.advice }}</p>
      <p v-for="(c, i) in advice.changes" :key="i" class="change">
        步骤 {{ c.stepNo }}：{{ c.text || '仅调整时长' }}
      </p>
      <button @click="doApply">应用修改</button>
      <button @click="advice = null">忽略</button>
    </div>
  </div>
  <div v-else>加载中…</div>
</template>

<style scoped>
.cook { max-width: 720px; margin: 0 auto; padding: 16px; background: #111; color: #fff; min-height: 100vh; box-sizing: border-box; }
.bar { height: 6px; background: #333; border-radius: 3px; overflow: hidden; }
.bar-inner { height: 100%; background: #07c160; transition: width .5s; }
.head { text-align: center; color: #aaa; }
.step-text { font-size: 24px; line-height: 1.6; margin: 32px 0; }
.timer { text-align: center; }
.remain { font-size: 64px; font-weight: 700; color: #07c160; }
.nav { display: flex; gap: 12px; justify-content: center; margin: 16px 0; }
.upload-btn { background: #07c160; color: #fff; padding: 6px 14px; border-radius: 6px; cursor: pointer; }
.photos { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 16px; }
.photo img { width: 100px; height: 100px; object-fit: cover; border-radius: 8px; display: block; }
.photo a { color: #07c160; font-size: 12px; margin-right: 8px; }
.photo .del { color: #e66; }
.advice { background: #1e1e1e; border-radius: 12px; padding: 16px; margin-top: 16px; }
.change { color: #ccc; font-size: 13px; }
</style>
```

- [ ] **Step 3: 路由注册**

`router.js`：import `CookMode`，routes 中 `{ path: '/recipes/:id', component: RecipeDetail, ... }` **之前**加：

```js
  { path: '/recipes/:id/cook', component: CookMode, meta: { requiresAuth: true } },
```

- [ ] **Step 4: RecipeDetail 改造**

1. import 行加 `listPhotos, deletePhoto, uploadPhoto`；SOURCE_TEXT 加 `PHOTO_ANALYSIS: '拍照修正'`。
2. 首卡片（编辑按钮旁）加：

```html
      <button class="edit-toggle" @click="$router.push(`/recipes/${id}/cook`)">开始烹饪</button>
```

3. script 末尾（`saveName` 后）加照片逻辑：

```js
const photos = ref([])

async function loadPhotos() {
  try { photos.value = await listPhotos(id) } catch (e) { /* 静默 */ }
}
loadPhotos()

async function onPhotoFile(e, stepNo) {
  const file = e.target.files[0]
  if (!file) return
  try {
    await uploadPhoto(id, stepNo, file)
    showToast('已上传')
    await loadPhotos()
  } catch (err) { showToast(err.message || '上传失败') }
  e.target.value = ''
}

async function doDeletePhoto(photoId) {
  try {
    await deletePhoto(photoId)
    await loadPhotos()
  } catch (e) { showToast(e.message || '删除失败') }
}

function photosOf(stepNo) { return photos.value.filter((p) => p.stepNo === stepNo) }
```

4. 步骤卡片（`.step` 循环内 `</div>` 前）追加：

```html
        <div class="photos" v-if="photosOf(s.no).length">
          <div class="photo-item" v-for="p in photosOf(s.no)" :key="p.id">
            <img :src="p.url" />
            <a href="#" @click.prevent="doDeletePhoto(p.id)">删</a>
          </div>
        </div>
        <label class="photo-upload">
          + 拍照
          <input type="file" accept="image/*" capture="environment" hidden
                 @change="onPhotoFile($event, s.no)" />
        </label>
```

5. style 末尾追加：

```css
.photos { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.photo-item img { width: 80px; height: 80px; object-fit: cover; border-radius: 6px; display: block; }
.photo-item a { color: #e66; font-size: 12px; }
.photo-upload { color: #07c160; font-size: 12px; cursor: pointer; align-self: center; }
```

（`load()` 成功后也顺带 `loadPhotos()`：在 `load()` 的 `loadFailed.value = false` 前加 `await loadPhotos()`。）

- [ ] **Step 5: 构建验证 + Commit**

Run: `cd web && npm run build`
Expected: 构建成功无错误

```bash
git add web/src/api/recipe.js web/src/views/CookMode.vue web/src/router.js web/src/views/RecipeDetail.vue
git commit -m "feat(p4): Web 烹饪模式页 + 照片上传/分析/应用"
```

---

### Task 8: 部署配置 + 文档收尾

**Files:**
- Modify: `deploy/docker-compose.yml`
- Modify: `deploy/.env.example`（若存在；不存在则在 README 说明）
- Modify: `README.md`
- Modify: `docs/PROJECT-STATUS.md`、`docs/TODO.md`

**Interfaces:**
- Consumes: `DASHSCOPE_API_KEY` / `DASHSCOPE_VL_MODEL` / `IMAGE_BASE_DIR`（application.yml 已引用）
- Produces: compose 全链路可启动；文档状态更新至"P4 开发完成"

- [ ] **Step 1: compose 配置**

`deploy/docker-compose.yml` app 服务：

1. environment 追加：

```yaml
      DASHSCOPE_API_KEY: ${DASHSCOPE_API_KEY:-}
      DASHSCOPE_VL_MODEL: ${DASHSCOPE_VL_MODEL:-qwen3-vl-flash}
      IMAGE_BASE_DIR: /data/images
```

2. volumes 段（app 服务当前无 volumes）追加：

```yaml
    volumes:
      - ../data/images:/data/images
```

3. 确认宿主机目录存在：`mkdir -p data/images`（本地已存在则跳过）。

- [ ] **Step 2: README 更新**

README 环境变量表（或配置说明节）补三行：

```
| DASHSCOPE_API_KEY | 阿里云百炼 API Key（P4 视觉分析；缺省时视觉分析返回 6005 降级） |
| DASHSCOPE_VL_MODEL | 视觉模型名，默认 qwen3-vl-flash |
| IMAGE_BASE_DIR | 图片存储目录，默认 /data/images（compose 挂载 ../data/images） |
```

- [ ] **Step 3: compose 冒烟**

Run: `cd deploy && docker compose up -d --build && sleep 15 && curl -s http://localhost/api/health && docker compose exec app ls /data/images`
Expected: health 返回 code=0；`/data/images` 目录在 app 容器内可见（可写）。

- [ ] **Step 4: 文档收尾**

1. `docs/TODO.md` 第 4 节 5 个条目全部 `[x]`（拍照上传、烹饪模式、计时引擎、AI 视觉分析、结果反馈写入迭代数据），并在第 6 节随手记录区加：
   - `🧑 阿里云百炼 API Key（DASHSCOPE_API_KEY）待配置到 deploy/.env —— P4 视觉分析真跑与统一验证依赖`
   - `P4 双端 UI 手工验证延后（与 P3 真机流式验证合并到统一验证阶段）`
2. `docs/PROJECT-STATUS.md`：
   - 第 4 节路线图 P4 行改为：`✅ P4 开发完成（2026-09-18，feature/p3-recipe-engine 分支）`
   - 第 6 节"下一步"改写：P4 完成描述（一句话：计时烹饪模式双端、照片上传本地卷、Qwen3-VL 视觉分析+补丁确认生成 PHOTO_ANALYSIS 版本）+ 下一步改为 P5 打磨
   - 末尾"最后更新"行更新
3. Commit + push：

```bash
git add deploy/docker-compose.yml README.md docs/TODO.md docs/PROJECT-STATUS.md
git commit -m "docs(p4): 部署配置与文档收尾(P4 开发完成)"
git push origin feature/p3-recipe-engine
```

---

## 验收清单（分支级终审前自查）

- [ ] `cd server && mvn clean test` 全绿（85 既有 + P4 新增）
- [ ] `cd web && npm run build` 通过
- [ ] compose 冒烟：health OK、app 容器可写 /data/images
- [ ] 全链路人工核对：上传→分析（mock 或真 Key）→应用→版本列表出现 PHOTO_ANALYSIS→回滚可用
- [ ] spec §4 API 与实现一一对应（含 6007/6008 两个补充码）
- [ ] 双端 UI 手工验证清单已记录到 TODO.md（延后统一验证）
