# P3 AI 菜谱引擎 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 link-life 后端 + 小程序/Web 双端实现 AI 菜谱引擎：菜谱生成（真流式 SSE）、版本化、口感反馈迭代闭环、调料架/手动编辑/个性化命名。

**Architecture:** 厚后端：新模块 `com.linklife.recipe`（entity/mapper/service/controller/sse）+ 扩展 `AiGatewayService`（结构化输出 + Flux 流式）。SSE 端点 POST + text/event-stream，delta 流原始 AI 文本，流结束后单事务落库再发 done。双端为薄壳：小程序 `wx.request enableChunked`，Web `fetch + ReadableStream`，共用 SSE 帧格式。

**Tech Stack:** Java 17 / Spring Boot 3.3.4 / Spring AI 1.0.0 (deepseek) / MyBatis-Plus 3.5.7 / Flyway / JUnit5 + MockMvc + Testcontainers / 微信小程序原生 / Vue 3 + Vite

**Spec:** `docs/superpowers/specs/2026-09-18-p3-recipe-engine-design.md`（实现过程中的裁决依据，与计划一起读）

## Global Constraints

- 分支：`feature/p3-recipe-engine`（已创建，基于 origin/main）。**不要**直接在 main 上提交。
- 后端代码全部在 `server/`；包根 `com.linklife`；新模块 `com.linklife.recipe`，提示词在 `com.linklife.ai.prompt`。
- REST 一律返回 `Result<T>`（`com.linklife.common.web.Result`）；业务错误抛 `BusinessException(ErrorCode.X)`；当前用户 `UserContext.requireUserId()`。
- 新错误码用 5xxx 段（Task 2 定义），禁止复用其他号段。
- 实体风格照抄 `com.linklife.order.entity.Dish`：`@Data @TableName @TableId(type = IdType.AUTO)`；Mapper 为空 `BaseMapper`；查询用 `LambdaQueryWrapper`。`createdAt/updatedAt` 在代码里显式赋值（MyBatis-Plus 不自动填充）。
- 测试基类 `com.linklife.IntegrationTestBase`（Testcontainers MySQL 单例 + `spring.ai.deepseek.api-key=test-key`）；AI 相关测试用 Mockito `RETURNS_DEEP_STUBS` 打桩 `ChatClient.Builder`（Task 4 有完整示例）。运行测试：`cd server && mvn clean test`（需本机 Docker）。
- 提交信息风格：`feat:` / `test:` / `fix:` / `docs:` / `deploy:`，小写。
- 敏感信息：**绝不**把 DEEPSEEK_API_KEY 等写入任何被跟踪文件（deploy/.env 已 gitignore）。
- 不上 Redis、不引入新中间件；流式用 Spring MVC `SseEmitter`（servlet 栈，不是 WebFlux）。
- Spring AI 1.0.0 的 `Usage` 接口取值方法为 `getPromptTokens()/getCompletionTokens()`（Integer）；若实际编译签名有出入，以编译器为准调整取值方式，不得改变调用日志语义（取不到记 null）。
- 计划中的测试代码若与本仓库既有夹具细节冲突（如 V1 表列名、wx-login 响应路径），以真实代码为准修正测试，不改业务语义。

---

### Task 1: Flyway V4 迁移 + 实体 + Mapper

**Files:**
- Create: `server/src/main/resources/db/migration/V4__recipe.sql`
- Create: `server/src/main/java/com/linklife/recipe/entity/Recipe.java`、`RecipeVersion.java`、`RecipeFeedback.java`、`PantryItem.java`
- Create: `server/src/main/java/com/linklife/recipe/mapper/RecipeMapper.java`（×4，同模式）
- Test: `server/src/test/java/com/linklife/recipe/RecipeMigrationTest.java`

**Interfaces:**
- Produces: 实体 `Recipe{id, dishId, customName, currentVersion, createdBy, createdAt, updatedAt}`、`RecipeVersion{id, recipeId, version, source, content(String), changeNote, createdBy, createdAt}`、`RecipeFeedback{id, recipeId, userId, score, comment, createdAt}`、`PantryItem{id, userId, type, name, note, createdAt}`（Long/String/Integer 字段 + `LocalDateTime` 时间，Lombok `@Data`）；Mapper `RecipeMapper/RecipeVersionMapper/RecipeFeedbackMapper/PantryItemMapper extends BaseMapper<T>`。后续任务以此为准。

- [ ] **Step 1: 写迁移 SQL**

```sql
-- V4__recipe.sql
CREATE TABLE recipe (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dish_id BIGINT NOT NULL,
    custom_name VARCHAR(64) NULL COMMENT '个性化命名，空则显示 dish.name',
    current_version INT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_dish (dish_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recipe_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    version INT NOT NULL COMMENT '从 1 递增',
    source VARCHAR(16) NOT NULL COMMENT 'AI_GENERATE / AI_ITERATE / MANUAL_EDIT',
    content JSON NOT NULL COMMENT '结构化菜谱',
    change_note VARCHAR(255) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_recipe_version (recipe_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE recipe_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score TINYINT NOT NULL COMMENT '1~5',
    comment VARCHAR(512) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_recipe (recipe_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pantry_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(16) NOT NULL COMMENT 'SEASONING / INGREDIENT',
    name VARCHAR(64) NOT NULL,
    note VARCHAR(128) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- dish 表补唯一键，支撑并发 upsert（dish 当前无数据，安全）
ALTER TABLE dish ADD UNIQUE KEY uk_circle_name (circle_id, name);
```

- [ ] **Step 2: 写 4 个实体**

`Recipe.java`（其余 3 个同模式，字段见 Interfaces）：

```java
package com.linklife.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recipe")
public class Recipe {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dishId;
    private String customName;
    private Integer currentVersion;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

`RecipeVersion.java`：

```java
package com.linklife.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recipe_version")
public class RecipeVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private Integer version;
    private String source;
    private String content;
    private String changeNote;
    private Long createdBy;
    private LocalDateTime createdAt;
}
```

`RecipeFeedback.java`：

```java
package com.linklife.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("recipe_feedback")
public class RecipeFeedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recipeId;
    private Long userId;
    private Integer score;
    private String comment;
    private LocalDateTime createdAt;
}
```

`PantryItem.java`：

```java
package com.linklife.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("pantry_item")
public class PantryItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;
    private String name;
    private String note;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 写 4 个 Mapper**

每个一文件，模式如下（替换类名/泛型为 RecipeVersion/RecipeFeedback/PantryItem）：

```java
package com.linklife.recipe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linklife.recipe.entity.Recipe;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecipeMapper extends BaseMapper<Recipe> {
}
```

- [ ] **Step 4: 写迁移冒烟测试**

```java
package com.linklife.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linklife.IntegrationTestBase;
import com.linklife.recipe.entity.PantryItem;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipeFeedback;
import com.linklife.recipe.entity.RecipeVersion;
import com.linklife.recipe.mapper.PantryItemMapper;
import com.linklife.recipe.mapper.RecipeFeedbackMapper;
import com.linklife.recipe.mapper.RecipeMapper;
import com.linklife.recipe.mapper.RecipeVersionMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RecipeMigrationTest extends IntegrationTestBase {

    @Autowired
    private RecipeMapper recipeMapper;
    @Autowired
    private RecipeVersionMapper recipeVersionMapper;
    @Autowired
    private RecipeFeedbackMapper recipeFeedbackMapper;
    @Autowired
    private PantryItemMapper pantryItemMapper;

    @Test
    void tablesExistAndBasicInsertWorks() {
        Recipe recipe = new Recipe();
        recipe.setDishId(1L);
        recipe.setCurrentVersion(1);
        recipe.setCreatedBy(1L);
        recipe.setCreatedAt(LocalDateTime.now());
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.insert(recipe);
        assertNotNull(recipe.getId());

        RecipeVersion version = new RecipeVersion();
        version.setRecipeId(recipe.getId());
        version.setVersion(1);
        version.setSource("AI_GENERATE");
        version.setContent("{\"servings\":2}");
        version.setCreatedBy(1L);
        version.setCreatedAt(LocalDateTime.now());
        recipeVersionMapper.insert(version);

        RecipeFeedback feedback = new RecipeFeedback();
        feedback.setRecipeId(recipe.getId());
        feedback.setUserId(1L);
        feedback.setScore(5);
        feedback.setCreatedAt(LocalDateTime.now());
        recipeFeedbackMapper.insert(feedback);

        PantryItem item = new PantryItem();
        item.setUserId(1L);
        item.setType("SEASONING");
        item.setName("生抽");
        item.setCreatedAt(LocalDateTime.now());
        pantryItemMapper.insert(item);

        assertEquals(1, recipeVersionMapper.selectCount(
                new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipe.getId())));
    }
}
```

- [ ] **Step 5: 运行测试**

Run: `cd server && mvn clean test -Dtest=RecipeMigrationTest`
Expected: PASS（Flyway 执行 V4 成功）

- [ ] **Step 6: Commit**

```bash
git add server/src/main/resources/db/migration/V4__recipe.sql server/src/main/java/com/linklife/recipe server/src/test/java/com/linklife/recipe/RecipeMigrationTest.java
git commit -m "feat: add V4 recipe tables with entities and mappers"
```

---

### Task 2: 错误码 5xxx 段

**Files:**
- Modify: `server/src/main/java/com/linklife/common/exception/ErrorCode.java`

**Interfaces:**
- Produces: `ErrorCode.RECIPE_NOT_FOUND(5001)`、`RECIPE_VERSION_LIMIT(5002)`、`RECIPE_AI_FAILED(5003)`、`RECIPE_PARSE_FAILED(5004)`、`PANTRY_ITEM_EXISTS(5005)`、`RECIPE_ALREADY_EXISTS(5006)`、`PANTRY_ITEM_NOT_FOUND(5007)`。后续任务按名引用。

- [ ] **Step 1: 把 `RATE_LIMITED(4001, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS);` 一行替换为：**

```java
    RATE_LIMITED(4001, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    RECIPE_NOT_FOUND(5001, "菜谱不存在", HttpStatus.NOT_FOUND),
    RECIPE_VERSION_LIMIT(5002, "已达版本上限，请先回滚旧版本", HttpStatus.BAD_REQUEST),
    RECIPE_AI_FAILED(5003, "AI 生成失败，请重试", HttpStatus.INTERNAL_SERVER_ERROR),
    RECIPE_PARSE_FAILED(5004, "AI 返回内容解析失败", HttpStatus.INTERNAL_SERVER_ERROR),
    PANTRY_ITEM_EXISTS(5005, "该条目已存在", HttpStatus.BAD_REQUEST),
    RECIPE_ALREADY_EXISTS(5006, "该菜品已有菜谱", HttpStatus.CONFLICT),
    PANTRY_ITEM_NOT_FOUND(5007, "条目不存在", HttpStatus.NOT_FOUND);
```

- [ ] **Step 2: 编译验证**

Run: `cd server && mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add server/src/main/java/com/linklife/common/exception/ErrorCode.java
git commit -m "feat: add 5xxx error codes for recipe module"
```

---

### Task 3: 结构化 DTO + AI 响应解析器

**Files:**
- Create: `server/src/main/java/com/linklife/recipe/dto/RecipeContent.java`、`IterationResult.java`
- Create: `server/src/main/java/com/linklife/user/dto/TasteSummary.java`
- Create: `server/src/main/java/com/linklife/ai/gateway/AiResponseParser.java`
- Test: `server/src/test/java/com/linklife/ai/AiResponseParserTest.java`

**Interfaces:**
- Consumes: `ErrorCode.RECIPE_PARSE_FAILED`（Task 2）。
- Produces:
  - `RecipeContent(Integer servings, Integer totalMinutes, List<Ingredient> ingredients, List<Ingredient> seasonings, List<Step> steps, String tips)`，内部 record `Ingredient(String name, String amount)`、`Step(Integer no, String text, Integer durationSec)`，方法 `void validate()`（ingredients/steps 非空，否则抛 RECIPE_PARSE_FAILED）。
  - `TasteSummary(String summary, List<String> tags)`（放 user 包：保持 recipe→user 依赖方向）。
  - `IterationResult(RecipeContent recipe, TasteSummary tasteSummary)`，方法 `void validate()`。
  - `AiResponseParser.extractJson(String raw): String`、`AiResponseParser.parse(String raw, Class<T> type): T`（静态；非法抛 `BusinessException(RECIPE_PARSE_FAILED)`）。

- [ ] **Step 1: 写 DTO**

`server/src/main/java/com/linklife/user/dto/TasteSummary.java`：

```java
package com.linklife.user.dto;

import java.util.List;

public record TasteSummary(String summary, List<String> tags) {
}
```

`server/src/main/java/com/linklife/recipe/dto/RecipeContent.java`：

```java
package com.linklife.recipe.dto;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import java.util.List;

public record RecipeContent(Integer servings, Integer totalMinutes,
                            List<Ingredient> ingredients, List<Ingredient> seasonings,
                            List<Step> steps, String tips) {

    public record Ingredient(String name, String amount) {
    }

    public record Step(Integer no, String text, Integer durationSec) {
    }

    public void validate() {
        if (ingredients == null || ingredients.isEmpty() || steps == null || steps.isEmpty()) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
    }
}
```

`server/src/main/java/com/linklife/recipe/dto/IterationResult.java`：

```java
package com.linklife.recipe.dto;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.user.dto.TasteSummary;

public record IterationResult(RecipeContent recipe, TasteSummary tasteSummary) {

    public void validate() {
        if (recipe == null) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
        recipe.validate();
    }
}
```

- [ ] **Step 2: 写解析器**

`server/src/main/java/com/linklife/ai/gateway/AiResponseParser.java`：

```java
package com.linklife.ai.gateway;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;

public final class AiResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AiResponseParser() {
    }

    /** 剥 markdown 围栏并截取首个 { 到末个 } 的 JSON 文本。 */
    public static String extractJson(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceAll("^```[a-zA-Z]*\\s*", "");
            int fenceEnd = s.lastIndexOf("```");
            if (fenceEnd >= 0) {
                s = s.substring(0, fenceEnd);
            }
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
        return s.substring(start, end + 1);
    }

    public static <T> T parse(String raw, Class<T> type) {
        try {
            return MAPPER.readValue(extractJson(raw), type);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
    }
}
```

- [ ] **Step 3: 写测试（纯 JUnit，不启 Spring）**

`server/src/test/java/com/linklife/ai/AiResponseParserTest.java`：

```java
package com.linklife.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.ai.gateway.AiResponseParser;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.recipe.dto.RecipeContent;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiResponseParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesPlainJson() {
        String raw = "{\"servings\":2,\"totalMinutes\":30,"
                + "\"ingredients\":[{\"name\":\"鸡蛋\",\"amount\":\"3个\"}],\"seasonings\":[],"
                + "\"steps\":[{\"no\":1,\"text\":\"打蛋\",\"durationSec\":60}],\"tips\":\"\"}";
        RecipeContent content = AiResponseParser.parse(raw, RecipeContent.class);
        assertEquals(2, content.servings());
        assertEquals("鸡蛋", content.ingredients().get(0).name());
        assertEquals(60, content.steps().get(0).durationSec());
    }

    @Test
    void stripsMarkdownFence() throws Exception {
        String raw = "```json\n{\"servings\":2,\"ingredients\":[{\"name\":\"鸡蛋\","
                + "\"amount\":\"3个\"}],\"steps\":[{\"no\":1,\"text\":\"打蛋\","
                + "\"durationSec\":60}]}\n```";
        JsonNode node = mapper.readTree(AiResponseParser.extractJson(raw));
        assertEquals(2, node.get("servings").asInt());
    }

    @Test
    void extractsFromSurroundingText() throws Exception {
        String raw = "好的，这是菜谱：\n{\"ingredients\":[{\"name\":\"鸡蛋\",\"amount\":\"3个\"}],"
                + "\"steps\":[{\"no\":1,\"text\":\"打蛋\",\"durationSec\":60}]}\n希望你喜欢";
        JsonNode node = mapper.readTree(AiResponseParser.extractJson(raw));
        assertEquals("鸡蛋", node.get("ingredients").get(0).get("name").asText());
    }

    @Test
    void rejectsGarbage() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> AiResponseParser.parse("这不是 JSON", RecipeContent.class));
        assertEquals(ErrorCode.RECIPE_PARSE_FAILED.code, e.getErrorCode().code);
    }

    @Test
    void validateRejectsEmptyIngredients() {
        RecipeContent content = new RecipeContent(2, 30, List.of(), List.of(),
                List.of(new RecipeContent.Step(1, "x", 60)), null);
        BusinessException e = assertThrows(BusinessException.class, content::validate);
        assertEquals(ErrorCode.RECIPE_PARSE_FAILED.code, e.getErrorCode().code);
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `cd server && mvn clean test -Dtest=AiResponseParserTest`
Expected: PASS（5 个用例全绿）

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/linklife/recipe/dto server/src/main/java/com/linklife/user/dto/TasteSummary.java server/src/main/java/com/linklife/ai/gateway/AiResponseParser.java server/src/test/java/com/linklife/ai/AiResponseParserTest.java
git commit -m "feat: add structured recipe DTOs and AI response parser"
```

---

### Task 4: AiGatewayService 重构（userId / callStructured / stream / tokens）

**Files:**
- Modify: `server/src/main/java/com/linklife/ai/gateway/AiCallLogger.java`
- Modify: `server/src/main/java/com/linklife/ai/gateway/AiGatewayService.java`（全文重写）
- Modify: `server/src/test/java/com/linklife/ai/AiGatewayServiceTest.java`（全文重写）

**Interfaces:**
- Consumes: `AiResponseParser`（Task 3）、`RecipeContent`/`IterationResult`（Task 3）。
- Produces（后续 Task 7/8 唯一 AI 入口）:
  - `String call(Long userId, String scene, String prompt)` — 阻塞调用，带 tokens 记录。
  - `<T> T callStructured(Long userId, String scene, String prompt, Class<T> type)` — 解析 + validate。
  - `Flux<String> stream(Long userId, String scene, String prompt)` — 增量文本流；成功/失败各自记 ai_call_log（流式 usage 取不到记 null）。
  - `void logFailure(Long userId, String scene, String errorMsg)` — 供编排层记解析/落库失败。
  - `AiCallLogger.log` 新签名：`log(Long userId, String scene, String provider, String model, boolean ok, String errorMsg, Integer promptTokens, Integer completionTokens)`。

- [ ] **Step 1: 扩展 AiCallLogger（8 参签名替换旧 6 参）**

`log` 方法替换为：

```java
    public void log(Long userId, String scene, String provider, String model,
                    boolean ok, String errorMsg, Integer promptTokens, Integer completionTokens) {
        try {
            AiCallLog entry = new AiCallLog();
            entry.setUserId(userId);
            entry.setScene(scene);
            entry.setProvider(provider);
            entry.setModel(model);
            entry.setOk(ok);
            entry.setErrorMsg(errorMsg);
            entry.setPromptTokens(promptTokens);
            entry.setCompletionTokens(completionTokens);
            entry.setCreatedAt(LocalDateTime.now());
            aiCallLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("failed to write ai_call_log", e);
        }
    }
```

- [ ] **Step 2: 写失败的网关测试（替换 AiGatewayServiceTest 全文）**

```java
package com.linklife.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.linklife.IntegrationTestBase;
import com.linklife.ai.gateway.AiCallLogger;
import com.linklife.ai.gateway.AiGatewayService;
import com.linklife.recipe.dto.RecipeContent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

class AiGatewayServiceTest extends IntegrationTestBase {

    static final String REPLY_JSON = "{\"servings\":2,\"totalMinutes\":30,"
            + "\"ingredients\":[{\"name\":\"鸡蛋\",\"amount\":\"3个\"}],\"seasonings\":[],"
            + "\"steps\":[{\"no\":1,\"text\":\"打蛋\",\"durationSec\":60}],\"tips\":\"\"}";

    @TestConfiguration
    static class FakeChatConfig {
        @Bean
        ChatClient.Builder chatClientBuilder() {
            return Mockito.mock(ChatClient.Builder.class,
                    Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        }
    }

    @MockBean
    private AiCallLogger aiCallLogger;

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private AiGatewayService aiGatewayService;

    @Test
    void callReturnsContentAndLogsWithTokens() {
        when(chatClientBuilder.build().prompt().user(anyString()).call().chatResponse())
                .thenReturn(deepChatResponse(REPLY_JSON, 10, 20));

        String answer = aiGatewayService.call(42L, "recipe_generate", "prompt");

        assertEquals(REPLY_JSON, answer);
        verify(aiCallLogger).log(eq(42L), eq("recipe_generate"), anyString(), anyString(),
                eq(true), isNull(), eq(10), eq(20));
    }

    @Test
    void callLogsFailureAndRethrows() {
        when(chatClientBuilder.build().prompt().user(anyString()).call().chatResponse())
                .thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class,
                () -> aiGatewayService.call(42L, "recipe_generate", "prompt"));

        verify(aiCallLogger).log(eq(42L), eq("recipe_generate"), anyString(), anyString(),
                eq(false), any(), isNull(), isNull());
    }

    @Test
    void callStructuredParsesAndValidates() {
        when(chatClientBuilder.build().prompt().user(anyString()).call().chatResponse())
                .thenReturn(deepChatResponse(REPLY_JSON, null, null));

        RecipeContent content = aiGatewayService.callStructured(
                42L, "recipe_generate", "prompt", RecipeContent.class);

        assertEquals("鸡蛋", content.ingredients().get(0).name());
    }

    @Test
    void streamEmitsChunksAndLogsSuccess() {
        when(chatClientBuilder.build().prompt().user(anyString()).stream().content())
                .thenReturn(Flux.just("{\"serv", "ings\":2}"));

        List<String> chunks = aiGatewayService.stream(42L, "recipe_generate", "prompt")
                .collectList().block();

        assertEquals(List.of("{\"serv", "ings\":2}"), chunks);
        verify(aiCallLogger).log(eq(42L), eq("recipe_generate"), anyString(), anyString(),
                eq(true), isNull(), isNull(), isNull());
    }

    @Test
    void streamLogsFailure() {
        when(chatClientBuilder.build().prompt().user(anyString()).stream().content())
                .thenReturn(Flux.error(new RuntimeException("net down")));

        assertThrows(RuntimeException.class,
                () -> aiGatewayService.stream(42L, "recipe_generate", "prompt").blockLast());

        verify(aiCallLogger).log(eq(42L), eq("recipe_generate"), anyString(), anyString(),
                eq(false), any(), isNull(), isNull());
    }

    /** 深桩 ChatResponse：output text 与 usage 两链。 */
    private static ChatResponse deepChatResponse(String text, Integer promptTokens,
                                                 Integer completionTokens) {
        ChatResponse response = mock(ChatResponse.class,
                Mockito.withSettings().defaultAnswer(Mockito.RETURNS_DEEP_STUBS));
        when(response.getResult()).thenReturn(new Generation(new AssistantMessage(text)));
        when(response.getMetadata()).thenReturn(mock(ChatResponseMetadata.class));
        Usage usage = mock(Usage.class);
        when(response.getMetadata().getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        when(usage.getCompletionTokens()).thenReturn(completionTokens);
        return response;
    }
}
```

若 `new Generation(new AssistantMessage(text))` 与 Spring AI 1.0.0 实际构造签名不符，以编译器为准调整桩写法（`AssistantMessage` 在 `org.springframework.ai.chat.messages` 包）。

- [ ] **Step 3: 运行测试确认失败**

Run: `cd server && mvn clean test -Dtest=AiGatewayServiceTest`
Expected: 编译失败（`AiGatewayService.call` 还是旧签名）——TDD 失败态。

- [ ] **Step 4: 重写 AiGatewayService（全文替换）**

```java
package com.linklife.ai.gateway;

import com.linklife.recipe.dto.IterationResult;
import com.linklife.recipe.dto.RecipeContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class AiGatewayService {

    private final ChatClient chatClient;
    private final AiCallLogger aiCallLogger;
    private final String provider;
    private final String model;

    public AiGatewayService(ChatClient.Builder chatClientBuilder,
                            AiCallLogger aiCallLogger,
                            @Value("${spring.ai.deepseek.api-key:}") String apiKey,
                            @Value("${spring.ai.deepseek.chat.options.model:deepseek-chat}") String model) {
        this.chatClient = chatClientBuilder.build();
        this.aiCallLogger = aiCallLogger;
        this.provider = "deepseek";
        this.model = model;
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

- [ ] **Step 5: 运行网关测试**

Run: `cd server && mvn clean test -Dtest=AiGatewayServiceTest`
Expected: PASS（5 个用例）

- [ ] **Step 6: 全量回归（确认旧调用方已全部迁移）**

Run: `cd server && mvn clean test`
Expected: BUILD SUCCESS（若编译报错于其他调用方，把该处改为新签名）

- [ ] **Step 7: Commit**

```bash
git add server/src/main/java/com/linklife/ai server/src/test/java/com/linklife/ai/AiGatewayServiceTest.java
git commit -m "feat: extend AiGatewayService with structured output, streaming and token logging"
```

---

### Task 5: 调料架模块（pantry）

**Files:**
- Create: `server/src/main/java/com/linklife/recipe/service/PantryService.java`
- Create: `server/src/main/java/com/linklife/recipe/controller/PantryController.java`
- Create: `server/src/main/java/com/linklife/recipe/dto/PantryAddRequest.java`
- Test: `server/src/test/java/com/linklife/recipe/PantryApiTest.java`

**Interfaces:**
- Consumes: `PantryItemMapper`（Task 1）、`ErrorCode.PANTRY_ITEM_EXISTS/PANTRY_ITEM_NOT_FOUND`（Task 2）、`UserContext.requireUserId()`。
- Produces:
  - REST：`GET /api/me/pantry` → `Result<List<PantryItem>>`；`POST /api/me/pantry` body `{"type":"SEASONING|INGREDIENT","name":"生抽","note":"可选"}` → `Result<PantryItem>`；`DELETE /api/me/pantry/{itemId}` → `Result<Void>`。
  - `PantryService.list(long userId): List<PantryItem>`、`add(long userId, String type, String name, String note): PantryItem`、`delete(long userId, long itemId): void`（Task 8 生成菜谱时读调料架）。

- [ ] **Step 1: 写失败的 API 测试**

```java
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
```

- [ ] **Step 2: 运行确认失败**

Run: `cd server && mvn clean test -Dtest=PantryApiTest`
Expected: 编译失败（PantryService 不存在）

- [ ] **Step 3: 实现 DTO / Service / Controller**

`server/src/main/java/com/linklife/recipe/dto/PantryAddRequest.java`：

```java
package com.linklife.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PantryAddRequest(
        @Pattern(regexp = "SEASONING|INGREDIENT", message = "类型无效") String type,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 128) String note) {
}
```

`server/src/main/java/com/linklife/recipe/service/PantryService.java`：

```java
package com.linklife.recipe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.recipe.entity.PantryItem;
import com.linklife.recipe.mapper.PantryItemMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PantryService {

    private final PantryItemMapper pantryItemMapper;

    public List<PantryItem> list(long userId) {
        return pantryItemMapper.selectList(new LambdaQueryWrapper<PantryItem>()
                .eq(PantryItem::getUserId, userId)
                .orderByDesc(PantryItem::getId));
    }

    public PantryItem add(long userId, String type, String name, String note) {
        Long dup = pantryItemMapper.selectCount(new LambdaQueryWrapper<PantryItem>()
                .eq(PantryItem::getUserId, userId)
                .eq(PantryItem::getName, name));
        if (dup > 0) {
            throw new BusinessException(ErrorCode.PANTRY_ITEM_EXISTS);
        }
        PantryItem item = new PantryItem();
        item.setUserId(userId);
        item.setType(type);
        item.setName(name);
        item.setNote(note);
        item.setCreatedAt(LocalDateTime.now());
        pantryItemMapper.insert(item);
        return item;
    }

    public void delete(long userId, long itemId) {
        int rows = pantryItemMapper.delete(new LambdaQueryWrapper<PantryItem>()
                .eq(PantryItem::getId, itemId)
                .eq(PantryItem::getUserId, userId));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.PANTRY_ITEM_NOT_FOUND);
        }
    }
}
```

`server/src/main/java/com/linklife/recipe/controller/PantryController.java`：

```java
package com.linklife.recipe.controller;

import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.recipe.dto.PantryAddRequest;
import com.linklife.recipe.entity.PantryItem;
import com.linklife.recipe.service.PantryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/pantry")
public class PantryController {

    private final PantryService pantryService;

    @GetMapping
    public Result<List<PantryItem>> list() {
        return Result.ok(pantryService.list(UserContext.requireUserId()));
    }

    @PostMapping
    public Result<PantryItem> add(@Valid @RequestBody PantryAddRequest request) {
        return Result.ok(pantryService.add(UserContext.requireUserId(),
                request.type(), request.name(), request.note()));
    }

    @DeleteMapping("/{itemId}")
    public Result<Void> delete(@PathVariable long itemId) {
        pantryService.delete(UserContext.requireUserId(), itemId);
        return Result.ok();
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `cd server && mvn clean test -Dtest=PantryApiTest`
Expected: PASS（4 个用例）

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/linklife/recipe/controller/PantryController.java server/src/main/java/com/linklife/recipe/service/PantryService.java server/src/main/java/com/linklife/recipe/dto/PantryAddRequest.java server/src/test/java/com/linklife/recipe/PantryApiTest.java
git commit -m "feat: add pantry item CRUD api"
```

---

### Task 6: 口味画像（taste_prefs 沉淀与读取）

**Files:**
- Create: `server/src/main/java/com/linklife/user/service/TasteProfileService.java`
- Modify: `server/src/main/java/com/linklife/user/MeController.java`
- Test: `server/src/test/java/com/linklife/user/TasteProfileTest.java`

**Interfaces:**
- Consumes: `UserProfileMapper`（已有）、`TasteSummary`（Task 3）。
- Produces:
  - `TasteProfileService.get(long userId): Map<String,Object>`（无数据返回空 map）
  - `TasteProfileService.getSummary(long userId): String`（无则 null；Task 8 拼 prompt 用）
  - `TasteProfileService.applySummary(long userId, TasteSummary summary): void`（@Transactional；summary 空/全空则不动；upsert user_profile 行，存储 JSON `{"summary": "...", "tags": [...]}`；沉淀失败只 warn 不抛——不阻断迭代主流程；Task 8 调用）
  - REST：`GET /api/me/taste-profile` → `Result<Map<String,Object>>`

- [ ] **Step 1: 读现有 MeController 与 AuthController**

先读 `MeController.java`（注入方式、返回风格）与 `AuthController.java`（确认 wx-login 响应里用户 id 的 JSON 路径，测试要用）。

- [ ] **Step 2: 写失败的测试**

```java
package com.linklife.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.linklife.IntegrationTestBase;
import com.linklife.auth.wechat.WeChatClient;
import com.linklife.auth.wechat.WxSession;
import com.linklife.user.dto.TasteSummary;
import com.linklife.user.service.TasteProfileService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class TasteProfileTest extends IntegrationTestBase {

    @MockBean
    private WeChatClient weChatClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TasteProfileService tasteProfileService;

    private MvcResult login(String openid) throws Exception {
        when(weChatClient.code2Session(anyString()))
                .thenReturn(new WxSession(openid, "unionid-" + openid));
        return mockMvc.perform(post("/api/auth/wx-login")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"c\"}"))
                .andExpect(status().isOk()).andReturn();
    }

    @Test
    void emptyProfileReturnsEmptyObject() throws Exception {
        String token = JsonPath.read(
                login("taste-user-1").getResponse().getContentAsString(), "$.data.accessToken");
        mockMvc.perform(get("/api/me/taste-profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap());
    }

    @Test
    void applySummaryUpsertsProfile() throws Exception {
        String body = login("taste-user-2").getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.accessToken");
        long userId = /* 按 Step 1 确认的路径取用户 id，如 */ JsonPath.read(body, "$.data.user.id");

        tasteProfileService.applySummary(userId,
                new TasteSummary("口味偏清淡、忌辣", List.of("清淡", "忌辣")));
        assertEquals("口味偏清淡、忌辣", tasteProfileService.getSummary(userId));

        // 覆盖式更新
        tasteProfileService.applySummary(userId, new TasteSummary("偏咸香", List.of("咸香")));
        assertEquals("偏咸香", tasteProfileService.getSummary(userId));

        // 空摘要不动画像
        tasteProfileService.applySummary(userId, null);
        assertEquals("偏咸香", tasteProfileService.getSummary(userId));

        // REST 端点读到最新值
        mockMvc.perform(get("/api/me/taste-profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("偏咸香"));
    }
}
```

实现时按 Step 1 结论修正 `$.data.user.id` 路径与未用 import。

- [ ] **Step 3: 运行确认失败**

Run: `cd server && mvn clean test -Dtest=TasteProfileTest`
Expected: 编译失败（TasteProfileService 不存在）

- [ ] **Step 4: 实现 TasteProfileService**

```java
package com.linklife.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.user.dto.TasteSummary;
import com.linklife.user.entity.UserProfile;
import com.linklife.user.mapper.UserProfileMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TasteProfileService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UserProfileMapper userProfileMapper;

    public Map<String, Object> get(long userId) {
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null || profile.getTastePrefs() == null) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(profile.getTastePrefs(),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            log.warn("failed to parse taste_prefs for user {}", userId, e);
            return Map.of();
        }
    }

    public String getSummary(long userId) {
        Object summary = get(userId).get("summary");
        return summary == null ? null : summary.toString();
    }

    @Transactional
    public void applySummary(long userId, TasteSummary summary) {
        if (summary == null || isBlank(summary.summary())
                && (summary.tags() == null || summary.tags().isEmpty())) {
            return;
        }
        try {
            String json = MAPPER.writeValueAsString(Map.of(
                    "summary", orEmpty(summary.summary()),
                    "tags", summary.tags() == null ? List.of() : summary.tags()));
            UserProfile profile = userProfileMapper.selectById(userId);
            if (profile == null) {
                profile = new UserProfile();
                profile.setUserId(userId);
                profile.setTastePrefs(json);
                profile.setCreatedAt(LocalDateTime.now());
                userProfileMapper.insert(profile);
            } else {
                profile.setTastePrefs(json);
                userProfileMapper.updateById(profile);
            }
        } catch (Exception e) {
            // 画像沉淀失败不阻断主流程
            log.warn("failed to save taste profile for user {}", userId, e);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
```

- [ ] **Step 5: MeController 加端点**

在 `MeController` 内新增（构造器注入 `TasteProfileService`，补齐 `java.util.Map` import）：

```java
    @GetMapping("/taste-profile")
    public Result<Map<String, Object>> tasteProfile() {
        return Result.ok(tasteProfileService.get(UserContext.requireUserId()));
    }
```

- [ ] **Step 6: 运行测试**

Run: `cd server && mvn clean test -Dtest=TasteProfileTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add server/src/main/java/com/linklife/user server/src/test/java/com/linklife/user/TasteProfileTest.java
git commit -m "feat: add taste profile read and AI summary persistence"
```

---

### Task 7: RecipeService（查询/反馈/编辑/回滚/落库核心）+ 非流式端点

**Files:**
- Create: `server/src/main/java/com/linklife/recipe/service/RecipeService.java`
- Create: `server/src/main/java/com/linklife/recipe/controller/RecipeController.java`（本任务只写非流式端点；SSE 端点 Task 8 追加）
- Create: `server/src/main/java/com/linklife/recipe/dto/FeedbackRequest.java`、`EditRecipeRequest.java`、`RollbackRequest.java`、`RecipeDetailVO.java`、`VersionMetaVO.java`、`RecipeVersionVO.java`
- Test: `server/src/test/java/com/linklife/recipe/RecipeApiTest.java`

**Interfaces:**
- Consumes: Task 1 实体/Mapper、Task 2 错误码、Task 3 DTO、`CircleService.requireMembership`（已有）、`DishMapper`（已有）。
- Produces（Task 8 依赖）:
  - `Recipe requireVisibleRecipe(long userId, long recipeId)`（圈外/不存在一律 RECIPE_NOT_FOUND）
  - `int versionCount(long recipeId)`；常量 `RecipeService.MAX_VERSIONS = 5`
  - `long createRecipeWithV1(long userId, long circleId, String dishName, RecipeContent content)` — @Transactional：dish upsert（并发安全）+ 建 recipe + 插 v1 + 条件回填 dish.recipeId；dish 已有菜谱抛 RECIPE_ALREADY_EXISTS
  - `IterateContext prepareIterate(long userId, long recipeId)` — record `IterateContext(String dishName, RecipeContent current, List<RecipeFeedback> feedbacks)`；版本 ≥5 抛 RECIPE_VERSION_LIMIT
  - `int saveIterated(long userId, long recipeId, IterationResult result, String changeNote)` — @Transactional：插 AI_ITERATE 版本 + 推进指针 + `tasteProfileService.applySummary`；返回新版本号
  - `static RecipeContent parseContent(String json)` / `static String toJson(RecipeContent)`（content 列 JSON ↔ RecipeContent）
  - REST：`GET /api/recipes/{id}`、`GET /api/recipes/by-dish?circleId=&dishName=`、`GET /api/recipes/{id}/versions`、`GET /api/recipes/{id}/versions/{version}`、`POST /api/recipes/{id}/feedback`、`PUT /api/recipes/{id}`、`POST /api/recipes/{id}/rollback`

- [ ] **Step 1: 读 V1 迁移确认夹具列名**

Run: 读 `server/src/main/resources/db/migration/V1__init.sql`，记下 `circle`/`circle_member`/`user` 的真实列清单（测试夹具要用原生 SQL 插行）。

- [ ] **Step 2: 写失败的 API 测试**

测试用 Mapper/JdbcTemplate 直接造数据（不依赖 AI）。`RecipeApiTest.java`（夹具列名按 Step 1 修正；`seedDish` 每次用唯一 invite_code 建新圈避免用例间冲突）：

```java
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
        Number userId = JsonPath.read(body, "$.data.user.id"); // 路径按 Step 1 修正
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
```

（`jsonPath("$.data.id").value((int) recipeId)` 若类型断言不匹配，改用 `value(recipeId + "")` 比较 string。）

- [ ] **Step 3: 运行确认失败**

Run: `cd server && mvn clean test -Dtest=RecipeApiTest`
Expected: 编译失败（RecipeService/RecipeController/VO 不存在）

- [ ] **Step 4: 实现 DTO（6 个 record）**

```java
// FeedbackRequest.java
package com.linklife.recipe.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @NotNull @Min(1) @Max(5) Integer score,
        @Size(max = 512) String comment) {
}
```

```java
// EditRecipeRequest.java
package com.linklife.recipe.dto;

import jakarta.validation.constraints.Size;

public record EditRecipeRequest(
        RecipeContent content,
        @Size(max = 64) String customName,
        @Size(max = 255) String changeNote) {
}
```

```java
// RollbackRequest.java
package com.linklife.recipe.dto;

import jakarta.validation.constraints.NotNull;

public record RollbackRequest(@NotNull Integer version) {
}
```

```java
// VersionMetaVO.java
package com.linklife.recipe.dto;

import java.time.LocalDateTime;

public record VersionMetaVO(int version, String source, String changeNote,
                            Long createdBy, LocalDateTime createdAt) {
}
```

```java
// RecipeVersionVO.java
package com.linklife.recipe.dto;

import java.time.LocalDateTime;

public record RecipeVersionVO(int version, String source, String changeNote,
                              RecipeContent content, LocalDateTime createdAt) {
}
```

```java
// RecipeDetailVO.java
package com.linklife.recipe.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RecipeDetailVO(long id, long dishId, String dishName, String customName,
                             int currentVersion, RecipeContent content,
                             List<VersionMetaVO> versions, LocalDateTime updatedAt) {
}
```

- [ ] **Step 5: 实现 RecipeService**

```java
package com.linklife.recipe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.circle.CircleService;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.order.entity.Dish;
import com.linklife.order.mapper.DishMapper;
import com.linklife.recipe.dto.IterationResult;
import com.linklife.recipe.dto.RecipeContent;
import com.linklife.recipe.dto.RecipeDetailVO;
import com.linklife.recipe.dto.RecipeVersionVO;
import com.linklife.recipe.dto.VersionMetaVO;
import com.linklife.recipe.entity.Recipe;
import com.linklife.recipe.entity.RecipeFeedback;
import com.linklife.recipe.entity.RecipeVersion;
import com.linklife.recipe.mapper.RecipeFeedbackMapper;
import com.linklife.recipe.mapper.RecipeMapper;
import com.linklife.recipe.mapper.RecipeVersionMapper;
import com.linklife.user.service.TasteProfileService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeService {

    public static final int MAX_VERSIONS = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RecipeMapper recipeMapper;
    private final RecipeVersionMapper recipeVersionMapper;
    private final RecipeFeedbackMapper recipeFeedbackMapper;
    private final DishMapper dishMapper;
    private final CircleService circleService;
    private final TasteProfileService tasteProfileService;

    // ---------- 权限与查询 ----------

    public Recipe requireVisibleRecipe(long userId, long recipeId) {
        Recipe recipe = recipeMapper.selectById(recipeId);
        if (recipe == null) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        Dish dish = dishMapper.selectById(recipe.getDishId());
        if (dish == null || dish.getCircleId() == null) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        try {
            circleService.requireMembership(userId, dish.getCircleId());
        } catch (BusinessException e) {
            // 圈外按不存在处理
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        return recipe;
    }

    public int versionCount(long recipeId) {
        Long count = recipeVersionMapper.selectCount(new LambdaQueryWrapper<RecipeVersion>()
                .eq(RecipeVersion::getRecipeId, recipeId));
        return count == null ? 0 : count.intValue();
    }

    public RecipeDetailVO get(long userId, long recipeId) {
        Recipe recipe = requireVisibleRecipe(userId, recipeId);
        Dish dish = dishMapper.selectById(recipe.getDishId());
        RecipeVersion current = requireVersionRow(recipeId, recipe.getCurrentVersion());
        return new RecipeDetailVO(recipe.getId(), dish.getId(), dish.getName(),
                recipe.getCustomName(), recipe.getCurrentVersion(),
                parseContent(current.getContent()), listVersionMetas(recipeId),
                recipe.getUpdatedAt());
    }

    public RecipeDetailVO findByDish(long userId, long circleId, String dishName) {
        circleService.requireMembership(userId, circleId);
        Dish dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getCircleId, circleId).eq(Dish::getName, dishName));
        if (dish == null || dish.getRecipeId() == null) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        return get(userId, dish.getRecipeId());
    }

    public List<VersionMetaVO> listVersions(long userId, long recipeId) {
        requireVisibleRecipe(userId, recipeId);
        return listVersionMetas(recipeId);
    }

    public RecipeVersionVO getVersion(long userId, long recipeId, int version) {
        requireVisibleRecipe(userId, recipeId);
        RecipeVersion row = requireVersionRow(recipeId, version);
        return new RecipeVersionVO(row.getVersion(), row.getSource(), row.getChangeNote(),
                parseContent(row.getContent()), row.getCreatedAt());
    }

    // ---------- 反馈 / 编辑 / 回滚 ----------

    @Transactional
    public void addFeedback(long userId, long recipeId, int score, String comment) {
        requireVisibleRecipe(userId, recipeId);
        RecipeFeedback feedback = new RecipeFeedback();
        feedback.setRecipeId(recipeId);
        feedback.setUserId(userId);
        feedback.setScore(score);
        feedback.setComment(comment);
        feedback.setCreatedAt(LocalDateTime.now());
        recipeFeedbackMapper.insert(feedback);
    }

    @Transactional
    public void edit(long userId, long recipeId, RecipeContent content,
                     String customName, String changeNote) {
        Recipe recipe = requireVisibleRecipe(userId, recipeId);
        if (content != null) {
            if (versionCount(recipeId) >= MAX_VERSIONS) {
                throw new BusinessException(ErrorCode.RECIPE_VERSION_LIMIT);
            }
            int next = versionCount(recipeId) + 1;
            insertVersion(recipeId, next, "MANUAL_EDIT", content, changeNote, userId);
            recipe.setCurrentVersion(next);
        }
        if (customName != null) {
            recipe.setCustomName(customName);
        }
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.updateById(recipe);
    }

    @Transactional
    public void rollback(long userId, long recipeId, int version) {
        Recipe recipe = requireVisibleRecipe(userId, recipeId);
        requireVersionRow(recipeId, version);
        recipe.setCurrentVersion(version);
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.updateById(recipe);
    }

    // ---------- 供流式编排使用（Task 8） ----------

    @Transactional
    public long createRecipeWithV1(long userId, long circleId, String dishName,
                                   RecipeContent content) {
        Dish dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getCircleId, circleId).eq(Dish::getName, dishName));
        if (dish == null) {
            dish = new Dish();
            dish.setCircleId(circleId);
            dish.setUserId(userId);
            dish.setName(dishName);
            try {
                dishMapper.insert(dish);
            } catch (DuplicateKeyException e) {
                // 并发 upsert：以唯一键 uk_circle_name 兜底后重查
                dish = dishMapper.selectOne(new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getCircleId, circleId).eq(Dish::getName, dishName));
            }
        }
        if (dish == null || dish.getRecipeId() != null) {
            throw new BusinessException(ErrorCode.RECIPE_ALREADY_EXISTS);
        }
        Recipe recipe = new Recipe();
        recipe.setDishId(dish.getId());
        recipe.setCurrentVersion(1);
        recipe.setCreatedBy(userId);
        recipe.setCreatedAt(LocalDateTime.now());
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.insert(recipe);
        insertVersion(recipe.getId(), 1, "AI_GENERATE", content, null, userId);

        // 条件更新防并发双 recipe：只有 recipe_id 还是 NULL 的一方成功
        int rows = dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                .eq(Dish::getId, dish.getId())
                .isNull(Dish::getRecipeId)
                .set(Dish::getRecipeId, recipe.getId()));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.RECIPE_ALREADY_EXISTS);
        }
        return recipe.getId();
    }

    public record IterateContext(String dishName, RecipeContent current,
                                 List<RecipeFeedback> feedbacks) {
    }

    public IterateContext prepareIterate(long userId, long recipeId) {
        Recipe recipe = requireVisibleRecipe(userId, recipeId);
        if (versionCount(recipeId) >= MAX_VERSIONS) {
            throw new BusinessException(ErrorCode.RECIPE_VERSION_LIMIT);
        }
        Dish dish = dishMapper.selectById(recipe.getDishId());
        RecipeVersion current = requireVersionRow(recipeId, recipe.getCurrentVersion());
        List<RecipeFeedback> feedbacks = recipeFeedbackMapper.selectList(
                new LambdaQueryWrapper<RecipeFeedback>()
                        .eq(RecipeFeedback::getRecipeId, recipeId)
                        .orderByDesc(RecipeFeedback::getId)
                        .last("LIMIT 5"));
        return new IterateContext(dish.getName(), parseContent(current.getContent()), feedbacks);
    }

    @Transactional
    public int saveIterated(long userId, long recipeId, IterationResult result,
                            String changeNote) {
        Recipe recipe = recipeMapper.selectById(recipeId);
        int next = versionCount(recipeId) + 1;
        insertVersion(recipeId, next, "AI_ITERATE", result.recipe(), changeNote, userId);
        recipe.setCurrentVersion(next);
        recipe.setUpdatedAt(LocalDateTime.now());
        recipeMapper.updateById(recipe);
        tasteProfileService.applySummary(userId, result.tasteSummary());
        return next;
    }

    // ---------- 内部 ----------

    public static RecipeContent parseContent(String json) {
        try {
            return MAPPER.readValue(json, RecipeContent.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
    }

    public static String toJson(RecipeContent content) {
        try {
            return MAPPER.writeValueAsString(content);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
    }

    private void insertVersion(long recipeId, int version, String source,
                               RecipeContent content, String changeNote, long userId) {
        RecipeVersion row = new RecipeVersion();
        row.setRecipeId(recipeId);
        row.setVersion(version);
        row.setSource(source);
        row.setContent(toJson(content));
        row.setChangeNote(changeNote);
        row.setCreatedBy(userId);
        row.setCreatedAt(LocalDateTime.now());
        recipeVersionMapper.insert(row);
    }

    private List<VersionMetaVO> listVersionMetas(long recipeId) {
        return recipeVersionMapper.selectList(new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipeId)
                        .orderByAsc(RecipeVersion::getVersion))
                .stream()
                .map(v -> new VersionMetaVO(v.getVersion(), v.getSource(), v.getChangeNote(),
                        v.getCreatedBy(), v.getCreatedAt()))
                .toList();
    }

    private RecipeVersion requireVersionRow(long recipeId, int version) {
        RecipeVersion row = recipeVersionMapper.selectOne(
                new LambdaQueryWrapper<RecipeVersion>()
                        .eq(RecipeVersion::getRecipeId, recipeId)
                        .eq(RecipeVersion::getVersion, version));
        if (row == null) {
            throw new BusinessException(ErrorCode.RECIPE_NOT_FOUND);
        }
        return row;
    }
}
```

- [ ] **Step 6: 实现 RecipeController（非流式部分）**

```java
package com.linklife.recipe.controller;

import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.recipe.dto.EditRecipeRequest;
import com.linklife.recipe.dto.FeedbackRequest;
import com.linklife.recipe.dto.RecipeDetailVO;
import com.linklife.recipe.dto.RecipeVersionVO;
import com.linklife.recipe.dto.RollbackRequest;
import com.linklife.recipe.dto.VersionMetaVO;
import com.linklife.recipe.service.RecipeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    @GetMapping("/{id}")
    public Result<RecipeDetailVO> get(@PathVariable long id) {
        return Result.ok(recipeService.get(UserContext.requireUserId(), id));
    }

    @GetMapping("/by-dish")
    public Result<RecipeDetailVO> byDish(@RequestParam long circleId,
                                         @RequestParam String dishName) {
        return Result.ok(recipeService.findByDish(UserContext.requireUserId(),
                circleId, dishName));
    }

    @GetMapping("/{id}/versions")
    public Result<List<VersionMetaVO>> versions(@PathVariable long id) {
        return Result.ok(recipeService.listVersions(UserContext.requireUserId(), id));
    }

    @GetMapping("/{id}/versions/{version}")
    public Result<RecipeVersionVO> version(@PathVariable long id, @PathVariable int version) {
        return Result.ok(recipeService.getVersion(UserContext.requireUserId(), id, version));
    }

    @PostMapping("/{id}/feedback")
    public Result<Void> feedback(@PathVariable long id,
                                 @Valid @RequestBody FeedbackRequest request) {
        recipeService.addFeedback(UserContext.requireUserId(), id,
                request.score(), request.comment());
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> edit(@PathVariable long id,
                             @Valid @RequestBody EditRecipeRequest request) {
        recipeService.edit(UserContext.requireUserId(), id,
                request.content(), request.customName(), request.changeNote());
        return Result.ok();
    }

    @PostMapping("/{id}/rollback")
    public Result<Void> rollback(@PathVariable long id,
                                 @Valid @RequestBody RollbackRequest request) {
        recipeService.rollback(UserContext.requireUserId(), id, request.version());
        return Result.ok();
    }
}
```

路由说明：Spring 对字面量 `by-dish` 的匹配优先于 `/{id}`，无需特殊处理。

- [ ] **Step 7: 运行测试**

Run: `cd server && mvn clean test -Dtest=RecipeApiTest`
Expected: PASS（10 个用例）

- [ ] **Step 8: Commit**

```bash
git add server/src/main/java/com/linklife/recipe server/src/test/java/com/linklife/recipe/RecipeApiTest.java
git commit -m "feat: add recipe query, feedback, edit and rollback api"
```

---

### Task 8: 流式生成与迭代（SSE）+ 提示词 + nginx

**Files:**
- Create: `server/src/main/java/com/linklife/ai/prompt/RecipePrompts.java`
- Create: `server/src/main/java/com/linklife/recipe/sse/RecipeExecutorConfig.java`
- Create: `server/src/main/java/com/linklife/recipe/sse/RecipeStreamService.java`
- Create: `server/src/main/java/com/linklife/recipe/service/RecipeGenerationService.java`
- Create: `server/src/main/java/com/linklife/recipe/dto/GenerateRequest.java`、`IterateRequest.java`
- Modify: `server/src/main/java/com/linklife/recipe/controller/RecipeController.java`（追加 2 个 SSE 端点）
- Modify: `deploy/nginx.conf`
- Test: `server/src/test/java/com/linklife/recipe/RecipeStreamTest.java`

**Interfaces:**
- Consumes: Task 4 网关（`stream`/`logFailure`）、Task 7 RecipeService（`createRecipeWithV1`/`prepareIterate`/`saveIterated`）、Task 5 `PantryService.list`、Task 6 `TasteProfileService.getSummary`、`CircleService.requireMembership`。
- Produces:
  - REST（SSE，POST + text/event-stream）：`POST /api/recipes/generate` body `{circleId, dishName}`；`POST /api/recipes/{id}/iterate` body `{comment}`。事件：`delta{text}` / `done{recipeId, version}` / `error{code, message}`。
  - `RecipeStreamService.newEmitter(): SseEmitter`（timeout 90s）、`sendDelta/sendDone/sendError`、`runAsync(SseEmitter, Runnable)`。
  - `RecipePrompts.generate(dishName, servings, pantry, tasteSummary)`、`RecipePrompts.iterate(dishName, current, feedbacks)`（静态）。

- [ ] **Step 1: 写提示词常量**

```java
package com.linklife.ai.prompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linklife.recipe.dto.RecipeContent;
import com.linklife.recipe.entity.PantryItem;
import com.linklife.recipe.entity.RecipeFeedback;
import java.util.List;

public final class RecipePrompts {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RecipePrompts() {
    }

    private static final String JSON_SHAPE = """
            {
              "servings": 2,
              "totalMinutes": 40,
              "ingredients": [{"name":"食材名","amount":"500g"}],
              "seasonings": [{"name":"调料名","amount":"2勺"}],
              "steps": [{"no":1,"text":"步骤描述","durationSec":300}],
              "tips": "小贴士"
            }""";

    public static String generate(String dishName, Integer servings, List<PantryItem> pantry,
                                  String tasteSummary) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为菜品「").append(dishName).append("」编写一份家庭菜谱。\n");
        sb.append("只输出一个 JSON 对象，不要输出任何解释文字或 markdown 代码围栏，结构如下：\n");
        sb.append(JSON_SHAPE).append('\n');
        sb.append("要求：步骤 4~10 步且每步标注 durationSec（秒）；")
          .append("用量用家庭可操作表述（如“2勺”“500g”）。\n");
        if (servings != null) {
            sb.append("几人食：").append(servings).append("。\n");
        }
        if (pantry != null && !pantry.isEmpty()) {
            List<String> seasonings = pantry.stream()
                    .filter(i -> "SEASONING".equals(i.getType()))
                    .map(PantryItem::getName).toList();
            List<String> ingredients = pantry.stream()
                    .filter(i -> "INGREDIENT".equals(i.getType()))
                    .map(PantryItem::getName).toList();
            if (!seasonings.isEmpty()) {
                sb.append("用户现有调料（调味尽量使用这些）：")
                        .append(String.join("、", seasonings)).append("。\n");
            }
            if (!ingredients.isEmpty()) {
                sb.append("用户现有食材（可优先使用）：")
                        .append(String.join("、", ingredients)).append("。\n");
            }
        }
        if (tasteSummary != null && !tasteSummary.isBlank()) {
            sb.append("用户口味画像（必须遵守忌口等约束）：").append(tasteSummary).append('\n');
        }
        return sb.toString();
    }

    public static String iterate(String dishName, RecipeContent current,
                                 List<RecipeFeedback> feedbacks) {
        StringBuilder sb = new StringBuilder();
        sb.append("这是菜品「").append(dishName).append("」的当前菜谱 JSON：\n");
        sb.append(toJson(current)).append('\n');
        sb.append("用户近期反馈：\n");
        for (RecipeFeedback f : feedbacks) {
            sb.append("- ").append(f.getScore()).append(" 星");
            if (f.getComment() != null && !f.getComment().isBlank()) {
                sb.append("：").append(f.getComment());
            }
            sb.append('\n');
        }
        sb.append("请基于反馈输出改进后的菜谱。只输出一个 JSON 对象，")
          .append("不要输出任何解释文字或 markdown 代码围栏，结构如下：\n");
        sb.append("{\"recipe\": <同上述菜谱结构>, \"taste_summary\": ")
          .append("{\"summary\": \"口味画像总结\", \"tags\": [\"标签\"]}}\n");
        sb.append("要求：针对反馈调整，未提及的部分保持稳定；")
          .append("taste_summary 总结用户口味偏好与忌口。\n");
        return sb.toString();
    }

    private static String toJson(RecipeContent content) {
        try {
            return MAPPER.writeValueAsString(content);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
```

- [ ] **Step 2: SSE 基建**

`server/src/main/java/com/linklife/recipe/sse/RecipeExecutorConfig.java`：

```java
package com.linklife.recipe.sse;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class RecipeExecutorConfig {

    @Bean("recipeExecutor")
    public Executor recipeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("recipe-sse-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

`server/src/main/java/com/linklife/recipe/sse/RecipeStreamService.java`：

```java
package com.linklife.recipe.sse;

import com.linklife.common.exception.ErrorCode;
import java.util.Map;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
public class RecipeStreamService {

    public static final long TIMEOUT_MS = 90_000;

    private final Executor recipeExecutor;

    public RecipeStreamService(@Qualifier("recipeExecutor") Executor recipeExecutor) {
        this.recipeExecutor = recipeExecutor;
    }

    public SseEmitter newEmitter() {
        return new SseEmitter(TIMEOUT_MS);
    }

    public void runAsync(SseEmitter emitter, Runnable work) {
        recipeExecutor.execute(() -> {
            try {
                work.run();
            } catch (Exception e) {
                log.error("recipe stream failed", e);
                sendError(emitter, ErrorCode.RECIPE_AI_FAILED);
            } finally {
                emitter.complete();
            }
        });
    }

    public void sendDelta(SseEmitter emitter, String text) {
        send(emitter, "delta", Map.of("text", text));
    }

    public void sendDone(SseEmitter emitter, long recipeId, int version) {
        send(emitter, "done", Map.of("recipeId", recipeId, "version", version));
    }

    public void sendError(SseEmitter emitter, ErrorCode code) {
        send(emitter, "error", Map.of("code", code.code, "message", code.message));
    }

    public void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.warn("sse send failed, event={}", event, e);
        }
    }
}
```

- [ ] **Step 3: 流式编排服务**

`server/src/main/java/com/linklife/recipe/service/RecipeGenerationService.java`：

```java
package com.linklife.recipe.service;

import com.linklife.ai.gateway.AiGatewayService;
import com.linklife.ai.gateway.AiResponseParser;
import com.linklife.ai.prompt.RecipePrompts;
import com.linklife.circle.CircleService;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.recipe.dto.IterationResult;
import com.linklife.recipe.dto.RecipeContent;
import com.linklife.recipe.entity.PantryItem;
import com.linklife.recipe.service.RecipeService.IterateContext;
import com.linklife.recipe.sse.RecipeStreamService;
import com.linklife.user.service.TasteProfileService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Disposable;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeGenerationService {

    private final AiGatewayService aiGatewayService;
    private final RecipeService recipeService;
    private final PantryService pantryService;
    private final TasteProfileService tasteProfileService;
    private final CircleService circleService;
    private final RecipeStreamService streamService;

    public SseEmitter generate(long userId, long circleId, String dishName) {
        // 同步校验：失败走全局异常处理（JSON 错误），不进流
        circleService.requireMembership(userId, circleId);
        List<PantryItem> pantry = pantryService.list(userId);
        String tasteSummary = tasteProfileService.getSummary(userId);
        String prompt = RecipePrompts.generate(dishName, 2, pantry, tasteSummary);
        return stream(userId, "recipe_generate", prompt, raw -> {
            RecipeContent content = AiResponseParser.parse(raw, RecipeContent.class);
            content.validate();
            long recipeId = recipeService.createRecipeWithV1(userId, circleId, dishName, content);
            return new long[]{recipeId, 1};
        });
    }

    public SseEmitter iterate(long userId, long recipeId, String comment) {
        IterateContext ctx = recipeService.prepareIterate(userId, recipeId);
        String prompt = RecipePrompts.iterate(ctx.dishName(), ctx.current(), ctx.feedbacks());
        return stream(userId, "recipe_iterate", prompt, raw -> {
            IterationResult result = AiResponseParser.parse(raw, IterationResult.class);
            result.validate();
            int version = recipeService.saveIterated(userId, recipeId, result, comment);
            return new long[]{recipeId, version};
        });
    }

    private interface PersistFn {
        long[] apply(String raw);
    }

    private SseEmitter stream(long userId, String scene, String prompt, PersistFn persist) {
        SseEmitter emitter = streamService.newEmitter();
        streamService.runAsync(emitter, () -> doStream(userId, scene, prompt, persist, emitter));
        return emitter;
    }

    private void doStream(long userId, String scene, String prompt, PersistFn persist,
                          SseEmitter emitter) {
        StringBuilder buffer = new StringBuilder();
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        emitter.onCompletion(() -> {
            Disposable d = subscription.get();
            if (d != null && !d.isDisposed()) {
                d.dispose();
            }
        });
        try {
            Flux<String> flux = aiGatewayService.stream(userId, scene, prompt)
                    .timeout(Duration.ofMillis(RecipeStreamService.TIMEOUT_MS - 5_000));
            subscription.set(flux.subscribe(
                    chunk -> {
                        buffer.append(chunk);
                        streamService.sendDelta(emitter, chunk);
                    },
                    err -> streamService.sendError(emitter, ErrorCode.RECIPE_AI_FAILED),
                    () -> {
                        try {
                            long[] ids = persist.apply(buffer.toString());
                            streamService.sendDone(emitter, ids[0], (int) ids[1]);
                        } catch (Exception e) {
                            log.warn("recipe persist failed, scene={}", scene, e);
                            aiGatewayService.logFailure(userId, scene,
                                    "PERSIST_FAILED: " + e.getMessage());
                            ErrorCode code = e instanceof BusinessException be
                                    ? be.getErrorCode() : ErrorCode.RECIPE_PARSE_FAILED;
                            streamService.sendError(emitter, code);
                        }
                    }));
        } catch (Exception e) {
            aiGatewayService.logFailure(userId, scene, "SUBSCRIBE_FAILED: " + e.getMessage());
            streamService.sendError(emitter, ErrorCode.RECIPE_AI_FAILED);
            throw e;
        }
    }
}
```

- [ ] **Step 4: Controller 追加 SSE 端点 + 请求 DTO**

`server/src/main/java/com/linklife/recipe/dto/GenerateRequest.java`：

```java
package com.linklife.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GenerateRequest(
        @NotNull Long circleId,
        @NotBlank @Size(max = 64) String dishName) {
}
```

`server/src/main/java/com/linklife/recipe/dto/IterateRequest.java`：

```java
package com.linklife.recipe.dto;

import jakarta.validation.constraints.Size;

public record IterateRequest(@Size(max = 512) String comment) {
}
```

`RecipeController` 追加字段与方法（补 import：`GenerateRequest`、`IterateRequest`、`RecipeGenerationService`、`jakarta.servlet.http.HttpServletResponse`、`org.springframework.http.MediaType`、`org.springframework.web.bind.annotation.PostMapping`、`org.springframework.web.servlet.mvc.method.annotation.SseEmitter`）：

```java
    private final RecipeGenerationService recipeGenerationService;

    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@Valid @RequestBody GenerateRequest request,
                               HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return recipeGenerationService.generate(UserContext.requireUserId(),
                request.circleId(), request.dishName());
    }

    @PostMapping(value = "/{id}/iterate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter iterate(@PathVariable long id,
                              @Valid @RequestBody IterateRequest request,
                              HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return recipeGenerationService.iterate(UserContext.requireUserId(),
                id, request.comment());
    }
```

- [ ] **Step 5: nginx 放行 SSE**

`deploy/nginx.conf` 在 `location /api/` **之前**插入（精确/正则 location 优先级高于前缀 `/api/`）：

```nginx
    # SSE 流式接口（菜谱生成/迭代）：关缓冲，超时放宽
    location = /api/recipes/generate {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 120s;
    }

    location ~ ^/api/recipes/[0-9]+/iterate$ {
        proxy_pass http://app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_http_version 1.1;
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 120s;
    }
```

- [ ] **Step 6: 写失败的集成测试（SSE 事件序 + 落库 + 失败路径）**

```java
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
        ChatClient.Builder chatClientBuilder() {
            return Mockito.mock(ChatClient.Builder.class,
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
    private ChatClient.Builder chatClientBuilder;
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
        when(chatClientBuilder.build().prompt().user(anyString()).stream().content())
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
        when(chatClientBuilder.build().prompt().user(anyString()).stream().content())
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
                        org.hamcrest.Matchers.containsString("5004")));

        assertEquals(0, dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getCircleId, circleId).eq(Dish::getName, "黑暗料理")));
        Mockito.verify(aiCallLogger).log(Mockito.any(), anyString(), anyString(), anyString(),
                Mockito.eq(false), Mockito.contains("PERSIST_FAILED"),
                Mockito.isNull(), Mockito.isNull());
    }

    @Test
    void iterateCreatesNewVersionAndUpdatesTastePrefs() throws Exception {
        when(chatClientBuilder.build().prompt().user(anyString()).stream().content())
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
        when(chatClientBuilder.build().prompt().user(anyString()).stream().content())
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
```

- [ ] **Step 7: 运行测试**

Run: `cd server && mvn clean test -Dtest=RecipeStreamTest`
Expected: PASS（4 个用例）。若 asyncDispatch 偶发未等到完成，加大 awaitAsync 轮询上限即可。

- [ ] **Step 8: 全量回归**

Run: `cd server && mvn clean test`
Expected: BUILD SUCCESS（51 + 新增 ≈ 71 测试）

- [ ] **Step 9: Commit**

```bash
git add server/src/main/java/com/linklife/ai/prompt server/src/main/java/com/linklife/recipe deploy/nginx.conf server/src/test/java/com/linklife/recipe/RecipeStreamTest.java
git commit -m "feat: add streaming recipe generation and iteration over SSE"
```

---

### Task 9: 小程序端（流式生成页 / 菜谱详情页 / 调料架页 / 入口）

**Files:**
- Create: `miniapp/utils/sse.js`
- Create: `miniapp/pages/recipe-generate/recipe-generate.{js,json,wxml,wxss}`
- Create: `miniapp/pages/recipe-detail/recipe-detail.{js,json,wxml,wxss}`
- Create: `miniapp/pages/pantry/pantry.{js,json,wxml,wxss}`
- Modify: `miniapp/app.json`（注册 3 个页面）
- Modify: `miniapp/pages/sheet-detail/sheet-detail.{js,wxml}`（item 加"菜谱"入口）
- Modify: `miniapp/pages/profile/profile.{js,wxml}`（加"调料架"入口）

**Interfaces:**
- Consumes: 后端 REST（Task 5/7/8）；`utils/request.js` 的 `request()`；`config.js` 的 `BASE_URL`。
- Produces: `miniapp/utils/sse.js` 导出 `streamRequest(path, data, handlers)`，`handlers = {onDelta(text), onDone({recipeId, version}), onError({code, message})}`。

- [ ] **Step 1: 写 SSE 工具**

```js
// miniapp/utils/sse.js
const { BASE_URL } = require('../config');

function arrayBufferToString(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  try {
    return decodeURIComponent(escape(binary));
  } catch (e) {
    // UTF-8 多字节被 chunk 截断时可能失败：退回原文，done 后以服务端数据为准
    return binary;
  }
}

function handleFrame(frame, handlers) {
  let event = 'message';
  let data = '';
  frame.split('\n').forEach((line) => {
    if (line.indexOf('event:') === 0) event = line.slice(6).trim();
    else if (line.indexOf('data:') === 0) data += line.slice(5).trim();
  });
  if (!data) return;
  let payload;
  try {
    payload = JSON.parse(data);
  } catch (e) {
    return;
  }
  if (event === 'delta' && handlers.onDelta) handlers.onDelta(payload.text);
  else if (event === 'done' && handlers.onDone) handlers.onDone(payload);
  else if (event === 'error' && handlers.onError) handlers.onError(payload);
}

/**
 * POST 流式请求（SSE over chunked）。需基础库 >= 2.20.1。
 * handlers: { onDelta(text), onDone({recipeId, version}), onError({code, message}) }
 */
function streamRequest(path, data, handlers) {
  const token = wx.getStorageSync('accessToken');
  const task = wx.request({
    url: BASE_URL + path,
    method: 'POST',
    data,
    enableChunked: true,
    timeout: 120000,
    header: Object.assign(
      { 'Content-Type': 'application/json' },
      token ? { Authorization: 'Bearer ' + token } : {}
    ),
    success: () => {},
    fail: (err) => {
      if (handlers.onError) handlers.onError({ code: -1, message: err.errMsg || '网络错误' });
    },
  });
  let buf = '';
  task.onChunkReceived((res) => {
    buf += arrayBufferToString(res.data);
    let idx;
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const frame = buf.slice(0, idx);
      buf = buf.slice(idx + 2);
      handleFrame(frame, handlers);
    }
  });
  return task;
}

module.exports = { streamRequest };
```

- [ ] **Step 2: 生成/迭代页**

`miniapp/pages/recipe-generate/recipe-generate.json`：

```json
{ "navigationBarTitleText": "生成菜谱" }
```

`miniapp/pages/recipe-generate/recipe-generate.wxml`：

```xml
<view class="page">
  <view class="stream-box">
    <text class="stream-text">{{streamText || '正在生成菜谱…'}}</text>
    <view wx:if="{{streaming}}" class="cursor">▍</view>
  </view>
  <view wx:if="{{errorText}}" class="error">{{errorText}}</view>
  <button wx:if="{{failed}}" bindtap="retry" type="primary">重试</button>
</view>
```

`miniapp/pages/recipe-generate/recipe-generate.js`：

```js
const { streamRequest } = require('../../utils/sse');

Page({
  data: {
    streamText: '',
    streaming: false,
    failed: false,
    errorText: '',
  },

  onLoad(options) {
    this.options = options;
    this.start();
  },

  start() {
    const self = this;
    this.setData({ streaming: true, failed: false, errorText: '', streamText: '' });
    const path = this.options.recipeId
      ? '/api/recipes/' + this.options.recipeId + '/iterate'
      : '/api/recipes/generate';
    const body = this.options.recipeId
      ? { comment: this.options.comment || '' }
      : { circleId: Number(this.options.circleId), dishName: this.options.dishName };
    this.task = streamRequest(path, body, {
      onDelta(text) {
        self.setData({ streamText: self.data.streamText + text });
      },
      onDone(payload) {
        self.setData({ streaming: false });
        wx.redirectTo({
          url: '/pages/recipe-detail/recipe-detail?id=' + payload.recipeId,
        });
      },
      onError(err) {
        self.setData({
          streaming: false,
          failed: true,
          errorText: (err && err.message) || '生成失败，请重试',
        });
      },
    });
  },

  retry() {
    this.start();
  },

  onUnload() {
    if (this.task && this.task.abort) this.task.abort();
  },
});
```

`miniapp/pages/recipe-generate/recipe-generate.wxss`：

```css
.page { padding: 24rpx; }
.stream-box {
  background: #fff; border-radius: 16rpx; padding: 32rpx;
  min-height: 400rpx; font-size: 26rpx; line-height: 1.7; color: #333;
  word-break: break-all; white-space: pre-wrap;
}
.cursor { display: inline-block; animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.error { color: #e64340; margin-top: 24rpx; font-size: 28rpx; }
```

- [ ] **Step 3: 菜谱详情页（含反馈/迭代/版本/回滚/命名）**

`miniapp/pages/recipe-detail/recipe-detail.json`：

```json
{ "navigationBarTitleText": "菜谱" }
```

`miniapp/pages/recipe-detail/recipe-detail.wxml`：

```xml
<view class="page" wx:if="{{recipe}}">
  <view class="card">
    <view class="title">{{recipe.customName || recipe.dishName}}</view>
    <view class="meta" wx:if="{{recipe.customName}}">原名：{{recipe.dishName}}</view>
    <view class="meta">版本 v{{recipe.currentVersion}} · 约 {{recipe.content.totalMinutes}} 分钟 · {{recipe.content.servings}} 人食</view>
  </view>

  <view class="card">
    <view class="section-title">食材</view>
    <view class="row" wx:for="{{recipe.content.ingredients}}" wx:key="name">
      <text>{{item.name}}</text><text class="amount">{{item.amount}}</text>
    </view>
    <view class="section-title">调料</view>
    <view class="row" wx:for="{{recipe.content.seasonings}}" wx:key="name">
      <text>{{item.name}}</text><text class="amount">{{item.amount}}</text>
    </view>
  </view>

  <view class="card">
    <view class="section-title">步骤</view>
    <view class="step" wx:for="{{recipe.content.steps}}" wx:key="no">
      <view class="step-no">{{item.no}}</view>
      <view class="step-body">
        <text>{{item.text}}</text>
        <text class="duration" wx:if="{{item.durationText}}">约 {{item.durationText}}</text>
      </view>
    </view>
    <view class="tips" wx:if="{{recipe.content.tips}}">小贴士：{{recipe.content.tips}}</view>
  </view>

  <view class="card">
    <view class="section-title">我的反馈</view>
    <view class="stars">
      <text wx:for="{{[1,2,3,4,5]}}" wx:key="*this"
            class="star {{item <= myScore ? 'on' : ''}}" bindtap="setScore"
            data-score="{{item}}">★</text>
    </view>
    <textarea class="comment" value="{{myComment}}" placeholder="口感如何？（如：偏淡了）"
              bindinput="onCommentInput" maxlength="512" />
    <button size="mini" bindtap="submitFeedback">提交反馈</button>
    <button size="mini" type="warn" plain bindtap="iterate" disabled="{{atLimit}}">按反馈优化菜谱</button>
    <view wx:if="{{atLimit}}" class="meta">已达版本上限，请先回滚旧版本</view>
  </view>

  <view class="card">
    <view class="section-title">历史版本（{{versions.length}}）</view>
    <view class="version" wx:for="{{versions}}" wx:key="version">
      <text>v{{item.version}} · {{item.sourceText}} · {{item.createdAtText}}</text>
      <text wx:if="{{item.changeNote}}">（{{item.changeNote}}）</text>
      <text wx:if="{{item.version !== recipe.currentVersion}}"
            class="rollback" bindtap="rollback" data-version="{{item.version}}">回滚到此版</text>
    </view>
  </view>

  <view class="card">
    <view class="section-title">个性化命名</view>
    <input class="name-input" value="{{nameInput}}" placeholder="如：我妈的红烧肉"
           bindinput="onNameInput" maxlength="64" />
    <button size="mini" bindtap="saveName">保存命名</button>
  </view>
</view>
<view wx:else class="page"><text>加载中…</text></view>
```

`miniapp/pages/recipe-detail/recipe-detail.js`：

```js
const { request } = require('../../utils/request');

const SOURCE_TEXT = {
  AI_GENERATE: 'AI 生成',
  AI_ITERATE: 'AI 迭代',
  MANUAL_EDIT: '手动编辑',
};

Page({
  data: {
    recipe: null,
    versions: [],
    myScore: 0,
    myComment: '',
    nameInput: '',
    atLimit: false,
  },

  onLoad(options) {
    this.recipeId = options.id;
  },

  onShow() {
    this.load();
  },

  load() {
    const self = this;
    request('/api/recipes/' + this.recipeId).then((recipe) => {
      (recipe.content.steps || []).forEach((s) => {
        s.durationText = s.durationSec >= 60
          ? Math.round(s.durationSec / 60) + ' 分钟'
          : (s.durationSec || 0) + ' 秒';
      });
      self.setData({
        recipe,
        nameInput: recipe.customName || '',
        atLimit: recipe.versions.length >= 5,
      });
      return request('/api/recipes/' + this.recipeId + '/versions');
    }).then((versions) => {
      versions.forEach((v) => {
        v.sourceText = SOURCE_TEXT[v.source] || v.source;
        v.createdAtText = (v.createdAt || '').replace('T', ' ').slice(0, 16);
      });
      self.setData({ versions });
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' });
    });
  },

  setScore(e) {
    this.setData({ myScore: Number(e.currentTarget.dataset.score) });
  },

  onCommentInput(e) {
    this.setData({ myComment: e.detail.value });
  },

  submitFeedback() {
    const { myScore } = this.data;
    if (!myScore) {
      wx.showToast({ title: '先点星星评分', icon: 'none' });
      return;
    }
    const self = this;
    request('/api/recipes/' + this.recipeId + '/feedback', {
      method: 'POST',
      data: { score: myScore, comment: this.data.myComment || null },
    }).then(() => {
      wx.showToast({ title: '已提交', icon: 'success' });
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '提交失败', icon: 'none' });
    });
  },

  iterate() {
    wx.navigateTo({
      url: '/pages/recipe-generate/recipe-generate?recipeId=' + this.recipeId,
    });
  },

  rollback(e) {
    const version = Number(e.currentTarget.dataset.version);
    const self = this;
    wx.showModal({
      title: '回滚确认',
      content: '回滚到 v' + version + '？历史版本不会删除',
      success(res) {
        if (!res.confirm) return;
        request('/api/recipes/' + self.recipeId + '/rollback', {
          method: 'POST',
          data: { version },
        }).then(() => self.load())
          .catch((err) => wx.showToast({
            title: (err && err.message) || '回滚失败', icon: 'none' }));
      },
    });
  },

  onNameInput(e) {
    this.setData({ nameInput: e.detail.value });
  },

  saveName() {
    const self = this;
    request('/api/recipes/' + this.recipeId, {
      method: 'PUT',
      data: { customName: this.data.nameInput || null },
    }).then(() => {
      wx.showToast({ title: '已保存', icon: 'success' });
      self.load();
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '保存失败', icon: 'none' });
    });
  },
});
```

`miniapp/pages/recipe-detail/recipe-detail.wxss`：

```css
.page { padding: 24rpx; }
.card { background: #fff; border-radius: 16rpx; padding: 28rpx; margin-bottom: 24rpx; }
.title { font-size: 36rpx; font-weight: 600; }
.meta { color: #999; font-size: 24rpx; margin-top: 8rpx; }
.section-title { font-size: 30rpx; font-weight: 600; margin: 16rpx 0; }
.row { display: flex; justify-content: space-between; padding: 8rpx 0; font-size: 28rpx; }
.amount { color: #666; }
.step { display: flex; margin: 16rpx 0; }
.step-no { width: 44rpx; height: 44rpx; border-radius: 50%; background: #07c160; color: #fff;
  text-align: center; line-height: 44rpx; margin-right: 16rpx; font-size: 24rpx; flex-shrink: 0; }
.step-body { flex: 1; font-size: 28rpx; }
.duration { display: block; color: #999; font-size: 24rpx; margin-top: 4rpx; }
.tips { color: #666; font-size: 26rpx; margin-top: 16rpx; }
.stars { font-size: 44rpx; color: #ddd; margin-bottom: 12rpx; }
.star.on { color: #f7ba2a; }
.comment { width: 100%; min-height: 100rpx; background: #f7f7f7; border-radius: 8rpx;
  padding: 16rpx; box-sizing: border-box; margin-bottom: 16rpx; }
.version { font-size: 26rpx; padding: 8rpx 0; color: #666; }
.rollback { color: #07c160; margin-left: 16rpx; }
.name-input { border: 1rpx solid #eee; border-radius: 8rpx; padding: 16rpx; margin-bottom: 16rpx; }
```

- [ ] **Step 4: 调料架页**

`miniapp/pages/pantry/pantry.json`：

```json
{ "navigationBarTitleText": "调料架" }
```

`miniapp/pages/pantry/pantry.wxml`：

```xml
<view class="page">
  <view class="add-box">
    <picker range="{{types}}" value="{{typeIndex}}" bindchange="onTypeChange">
      <view class="picker">{{types[typeIndex]}}</view>
    </picker>
    <input class="input" value="{{name}}" placeholder="如：生抽 / 五花肉"
           bindinput="onNameInput" />
    <button size="mini" type="primary" bindtap="add">添加</button>
  </view>
  <view class="item" wx:for="{{items}}" wx:key="id">
    <view>
      <text class="tag {{item.type === 'SEASONING' ? 'tag-s' : 'tag-i'}}">
        {{item.type === 'SEASONING' ? '调料' : '食材'}}</text>
      <text>{{item.name}}</text>
    </view>
    <text class="del" bindtap="del" data-id="{{item.id}}">删除</text>
  </view>
  <view wx:if="{{!items.length}}" class="empty">还没有条目，添加后 AI 会优先使用它们调味</view>
</view>
```

`miniapp/pages/pantry/pantry.js`：

```js
const { request } = require('../../utils/request');

Page({
  data: {
    items: [],
    types: ['调料', '食材'],
    typeIndex: 0,
    name: '',
  },

  onShow() {
    this.load();
  },

  load() {
    const self = this;
    request('/api/me/pantry').then((items) => self.setData({ items }))
      .catch(() => {});
  },

  onTypeChange(e) {
    this.setData({ typeIndex: Number(e.detail.value) });
  },

  onNameInput(e) {
    this.setData({ name: e.detail.value });
  },

  add() {
    const name = (this.data.name || '').trim();
    if (!name) {
      wx.showToast({ title: '请输入名称', icon: 'none' });
      return;
    }
    const self = this;
    request('/api/me/pantry', {
      method: 'POST',
      data: {
        type: this.data.typeIndex === 0 ? 'SEASONING' : 'INGREDIENT',
        name,
      },
    }).then(() => {
      self.setData({ name: '' });
      self.load();
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '添加失败', icon: 'none' });
    });
  },

  del(e) {
    const id = e.currentTarget.dataset.id;
    const self = this;
    request('/api/me/pantry/' + id, { method: 'DELETE' })
      .then(() => self.load())
      .catch((err) => wx.showToast({
        title: (err && err.message) || '删除失败', icon: 'none' }));
  },
});
```

`miniapp/pages/pantry/pantry.wxss`：

```css
.page { padding: 24rpx; }
.add-box { display: flex; align-items: center; gap: 16rpx; margin-bottom: 24rpx; }
.picker { background: #fff; padding: 16rpx 24rpx; border-radius: 8rpx; font-size: 28rpx; }
.input { flex: 1; background: #fff; padding: 16rpx 24rpx; border-radius: 8rpx; font-size: 28rpx; }
.item { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx;
  display: flex; justify-content: space-between; align-items: center; font-size: 28rpx; }
.tag { font-size: 20rpx; padding: 4rpx 12rpx; border-radius: 6rpx; margin-right: 12rpx; }
.tag-s { background: #e8f8ef; color: #07c160; }
.tag-i { background: #e8f1fd; color: #1989fa; }
.del { color: #e64340; }
.empty { color: #999; text-align: center; padding: 80rpx 0; font-size: 26rpx; }
```

- [ ] **Step 5: 注册页面 + 加入口**

`miniapp/app.json` 的 `pages` 数组追加（保持 JSON 合法）：

```json
"pages/recipe-generate/recipe-generate",
"pages/recipe-detail/recipe-detail",
"pages/pantry/pantry"
```

`miniapp/pages/sheet-detail/` 入口：先读 `sheet-detail.js` 确认 item 数据结构与圈 id 字段（`SheetDetailVO` 的 `circleId` 字段名以实际响应为准）。在 `sheet-detail.wxml` 的 item 行内（登录态、非 readonly 时显示）追加：

```xml
<text class="recipe-link" wx:if="{{!readonly}}"
      bindtap="openRecipe" data-name="{{item.dishName}}">菜谱</text>
```

`sheet-detail.js` 追加方法：

```js
openRecipe(e) {
  const dishName = e.currentTarget.dataset.name;
  const circleId = this.data.sheet.circleId;
  request('/api/recipes/by-dish?circleId=' + circleId + '&dishName=' +
      encodeURIComponent(dishName))
    .then((recipe) => {
      wx.navigateTo({ url: '/pages/recipe-detail/recipe-detail?id=' + recipe.id });
    })
    .catch(() => {
      wx.navigateTo({
        url: '/pages/recipe-generate/recipe-generate?circleId=' + circleId +
          '&dishName=' + encodeURIComponent(dishName),
      });
    });
},
```

`miniapp/pages/profile/profile.wxml` 菜单区追加（跟随现有菜单项写法）：

```xml
<view class="menu-item" bindtap="goPantry">调料架</view>
```

`profile.js` 追加：

```js
goPantry() {
  wx.navigateTo({ url: '/pages/pantry/pantry' });
},
```

- [ ] **Step 6: 语法自检 + Commit**

Run: `cd miniapp && node -e "JSON.parse(require('fs').readFileSync('app.json','utf8')); console.log('app.json ok')"`
Expected: `app.json ok`

```bash
git add miniapp
git commit -m "feat: add recipe generate/detail and pantry pages to miniapp"
```

---

### Task 10: Web 端（流式生成 / 菜谱详情 / 调料架 / 入口）

**Files:**
- Create: `web/src/api/sse.js`、`web/src/api/recipe.js`
- Create: `web/src/views/RecipeGenerate.vue`、`RecipeDetail.vue`、`Pantry.vue`
- Modify: `web/src/router.js`
- Modify: `web/src/views/SheetDetail.vue`

**Interfaces:**
- Consumes: 后端 REST/SSE（Task 5/7/8）；`api/request.js` 的 `request`。
- Produces: `api/sse.js` 导出 `streamRequest(path, data, handlers)`（与小程序同语义）。

- [ ] **Step 1: SSE 工具 + API 封装**

```js
// web/src/api/sse.js
function handleFrame(frame, handlers) {
  let event = 'message'
  let data = ''
  frame.split('\n').forEach((line) => {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data += line.slice(5).trim()
  })
  if (!data) return
  let payload
  try {
    payload = JSON.parse(data)
  } catch (e) {
    return
  }
  if (event === 'delta' && handlers.onDelta) handlers.onDelta(payload.text)
  else if (event === 'done' && handlers.onDone) handlers.onDone(payload)
  else if (event === 'error' && handlers.onError) handlers.onError(payload)
}

/**
 * POST SSE。handlers: { onDelta(text), onDone({recipeId, version}), onError({code, message}) }
 */
export async function streamRequest(path, data, handlers) {
  const token = localStorage.getItem('accessToken')
  let res
  try {
    res = await fetch(path, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
      },
      body: JSON.stringify(data),
    })
  } catch (e) {
    handlers.onError({ code: -1, message: '网络错误' })
    return
  }
  if (!res.ok || !res.body) {
    handlers.onError({ code: -1, message: '请求失败(' + res.status + ')' })
    return
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buf = ''
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    let idx
    while ((idx = buf.indexOf('\n\n')) >= 0) {
      const frame = buf.slice(0, idx)
      buf = buf.slice(idx + 2)
      handleFrame(frame, handlers)
    }
  }
}
```

```js
// web/src/api/recipe.js
import { request } from './request'

export const getRecipe = (id) => request('/api/recipes/' + id)
export const getVersions = (id) => request('/api/recipes/' + id + '/versions')
export const getByDish = (circleId, dishName) =>
  request('/api/recipes/by-dish?circleId=' + circleId + '&dishName=' +
    encodeURIComponent(dishName))
export const submitFeedback = (id, score, comment) =>
  request('/api/recipes/' + id + '/feedback', { method: 'POST', data: { score, comment } })
export const editRecipe = (id, data) =>
  request('/api/recipes/' + id, { method: 'PUT', data })
export const rollback = (id, version) =>
  request('/api/recipes/' + id + '/rollback', { method: 'POST', data: { version } })
export const listPantry = () => request('/api/me/pantry')
export const addPantry = (data) => request('/api/me/pantry', { method: 'POST', data })
export const deletePantry = (id) => request('/api/me/pantry/' + id, { method: 'DELETE' })
```

- [ ] **Step 2: RecipeGenerate.vue**

```vue
<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { streamRequest } from '../api/sse'

const route = useRoute()
const router = useRouter()
const streamText = ref('')
const failed = ref(false)
const errorText = ref('')

function start() {
  streamText.value = ''
  failed.value = false
  errorText.value = ''
  const isIterate = !!route.query.recipeId
  const path = isIterate
    ? '/api/recipes/' + route.query.recipeId + '/iterate'
    : '/api/recipes/generate'
  const body = isIterate
    ? { comment: route.query.comment || '' }
    : { circleId: Number(route.query.circleId), dishName: route.query.dishName }
  streamRequest(path, body, {
    onDelta: (text) => { streamText.value += text },
    onDone: (payload) => router.replace('/recipes/' + payload.recipeId),
    onError: (err) => {
      failed.value = true
      errorText.value = err.message || '生成失败，请重试'
    },
  })
}
start()
</script>

<template>
  <div class="generate">
    <pre class="stream">{{ streamText || '正在生成菜谱…' }}</pre>
    <p v-if="errorText" class="error">{{ errorText }}</p>
    <button v-if="failed" @click="start">重试</button>
  </div>
</template>

<style scoped>
.generate { max-width: 720px; margin: 0 auto; padding: 24px 16px; }
.stream { background: #fff; border-radius: 12px; padding: 24px; min-height: 320px;
  white-space: pre-wrap; word-break: break-all; font-size: 14px; line-height: 1.8; }
.error { color: #e64340; margin-top: 12px; }
</style>
```

- [ ] **Step 3: RecipeDetail.vue**

```vue
<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { getRecipe, getVersions, submitFeedback, editRecipe, rollback } from '../api/recipe'
import { showToast } from '../utils/toast'

const route = useRoute()
const id = route.params.id
const recipe = ref(null)
const versions = ref([])
const myScore = ref(0)
const myComment = ref('')
const nameInput = ref('')
const atLimit = computed(() => versions.value.length >= 5)

const SOURCE_TEXT = { AI_GENERATE: 'AI 生成', AI_ITERATE: 'AI 迭代', MANUAL_EDIT: '手动编辑' }

async function load() {
  recipe.value = await getRecipe(id)
  nameInput.value = recipe.value.customName || ''
  versions.value = (await getVersions(id)).map((v) => ({
    ...v,
    sourceText: SOURCE_TEXT[v.source] || v.source,
    createdAtText: (v.createdAt || '').replace('T', ' ').slice(0, 16),
  }))
}
load()

async function doFeedback() {
  if (!myScore.value) { showToast('先点星星评分'); return }
  try {
    await submitFeedback(id, myScore.value, myComment.value || null)
    showToast('已提交')
  } catch (e) { showToast(e.message || '提交失败') }
}

async function doRollback(v) {
  try {
    await rollback(id, v)
    await load()
  } catch (e) { showToast(e.message || '回滚失败') }
}

async function saveName() {
  try {
    await editRecipe(id, { customName: nameInput.value || null })
    showToast('已保存')
    await load()
  } catch (e) { showToast(e.message || '保存失败') }
}
</script>

<template>
  <div class="detail" v-if="recipe">
    <div class="card">
      <h2>{{ recipe.customName || recipe.dishName }}</h2>
      <p v-if="recipe.customName" class="meta">原名：{{ recipe.dishName }}</p>
      <p class="meta">版本 v{{ recipe.currentVersion }} ·
        约 {{ recipe.content.totalMinutes }} 分钟 · {{ recipe.content.servings }} 人食</p>
    </div>

    <div class="card">
      <h3>食材</h3>
      <div class="row" v-for="i in recipe.content.ingredients" :key="i.name">
        <span>{{ i.name }}</span><span class="amount">{{ i.amount }}</span>
      </div>
      <h3>调料</h3>
      <div class="row" v-for="i in recipe.content.seasonings" :key="i.name">
        <span>{{ i.name }}</span><span class="amount">{{ i.amount }}</span>
      </div>
    </div>

    <div class="card">
      <h3>步骤</h3>
      <div class="step" v-for="s in recipe.content.steps" :key="s.no">
        <span class="no">{{ s.no }}</span>
        <div>
          <p>{{ s.text }}</p>
          <p class="duration" v-if="s.durationSec">约
            {{ s.durationSec >= 60 ? Math.round(s.durationSec / 60) + ' 分钟' : s.durationSec + ' 秒' }}</p>
        </div>
      </div>
      <p v-if="recipe.content.tips" class="meta">小贴士：{{ recipe.content.tips }}</p>
    </div>

    <div class="card">
      <h3>我的反馈</h3>
      <div class="stars">
        <span v-for="n in 5" :key="n" :class="['star', { on: n <= myScore }]"
              @click="myScore = n">★</span>
      </div>
      <textarea v-model="myComment" placeholder="口感如何？（如：偏淡了）" maxlength="512" />
      <div class="btns">
        <button @click="doFeedback">提交反馈</button>
        <button class="warn" :disabled="atLimit"
                @click="$router.push({ path: '/recipes/generate', query: { recipeId: id } })">
          按反馈优化菜谱</button>
      </div>
      <p v-if="atLimit" class="meta">已达版本上限，请先回滚旧版本</p>
    </div>

    <div class="card">
      <h3>历史版本（{{ versions.length }}）</h3>
      <div class="version" v-for="v in versions" :key="v.version">
        <span>v{{ v.version }} · {{ v.sourceText }} · {{ v.createdAtText }}
          <template v-if="v.changeNote">（{{ v.changeNote }}）</template></span>
        <a v-if="v.version !== recipe.currentVersion"
           @click.prevent="doRollback(v.version)" href="#">回滚到此版</a>
      </div>
    </div>

    <div class="card">
      <h3>个性化命名</h3>
      <input v-model="nameInput" placeholder="如：我妈的红烧肉" maxlength="64" />
      <button @click="saveName">保存命名</button>
    </div>
  </div>
</template>

<style scoped>
.detail { max-width: 720px; margin: 0 auto; padding: 16px; }
.card { background: #fff; border-radius: 12px; padding: 20px; margin-bottom: 16px; }
.meta { color: #999; font-size: 13px; }
.row { display: flex; justify-content: space-between; padding: 4px 0; }
.amount { color: #666; }
.step { display: flex; gap: 12px; margin: 12px 0; }
.no { width: 24px; height: 24px; border-radius: 50%; background: #07c160; color: #fff;
  text-align: center; line-height: 24px; font-size: 12px; flex-shrink: 0; }
.duration { color: #999; font-size: 12px; }
.stars { font-size: 28px; color: #ddd; cursor: pointer; }
.star.on { color: #f7ba2a; }
textarea { width: 100%; min-height: 80px; margin: 12px 0; box-sizing: border-box; }
.btns { display: flex; gap: 12px; }
.version { display: flex; justify-content: space-between; font-size: 13px;
  color: #666; padding: 4px 0; }
.version a { color: #07c160; cursor: pointer; }
</style>
```

（`showToast` 以 `web/src/utils/toast.js` 实际导出为准；不同则改 import。）

- [ ] **Step 4: Pantry.vue**

```vue
<script setup>
import { onMounted, ref } from 'vue'
import { addPantry, deletePantry, listPantry } from '../api/recipe'
import { showToast } from '../utils/toast'

const items = ref([])
const type = ref('SEASONING')
const name = ref('')

async function load() {
  items.value = await listPantry()
}
onMounted(load)

async function add() {
  if (!name.value.trim()) { showToast('请输入名称'); return }
  try {
    await addPantry({ type: type.value, name: name.value.trim() })
    name.value = ''
    await load()
  } catch (e) { showToast(e.message || '添加失败') }
}

async function del(id) {
  try {
    await deletePantry(id)
    await load()
  } catch (e) { showToast(e.message || '删除失败') }
}
</script>

<template>
  <div class="pantry">
    <div class="add">
      <select v-model="type">
        <option value="SEASONING">调料</option>
        <option value="INGREDIENT">食材</option>
      </select>
      <input v-model="name" placeholder="如：生抽 / 五花肉" @keyup.enter="add" />
      <button @click="add">添加</button>
    </div>
    <div class="item" v-for="i in items" :key="i.id">
      <span><b class="tag">{{ i.type === 'SEASONING' ? '调料' : '食材' }}</b> {{ i.name }}</span>
      <a @click.prevent="del(i.id)" href="#">删除</a>
    </div>
    <p v-if="!items.length" class="empty">还没有条目，添加后 AI 会优先使用它们调味</p>
  </div>
</template>

<style scoped>
.pantry { max-width: 640px; margin: 0 auto; padding: 16px; }
.add { display: flex; gap: 8px; margin-bottom: 16px; }
.add input { flex: 1; }
.item { background: #fff; border-radius: 8px; padding: 12px 16px; margin-bottom: 8px;
  display: flex; justify-content: space-between; }
.item a { color: #e64340; cursor: pointer; }
.tag { font-weight: 400; font-size: 12px; color: #1989fa; margin-right: 8px; }
.empty { color: #999; text-align: center; padding: 48px 0; }
</style>
```

- [ ] **Step 5: 路由 + SheetDetail 入口**

`web/src/router.js` 追加（跟随现有路由写法）：

```js
{ path: '/recipes/generate', component: RecipeGenerate },
{ path: '/recipes/:id', component: RecipeDetail },
{ path: '/pantry', component: Pantry },
```

`web/src/views/SheetDetail.vue`：item 列表行内加"菜谱"链接（登录态显示；先读该文件确认 item 结构与 `sheet.circleId` 字段名）：

```vue
<a class="recipe-link" @click.prevent="openRecipe(item.dishName)" href="#">菜谱</a>
```

```js
import { getByDish } from '../api/recipe'

async function openRecipe(dishName) {
  try {
    const recipe = await getByDish(sheet.value.circleId, dishName)
    router.push('/recipes/' + recipe.id)
  } catch (e) {
    if (e.code === 5001) {
      router.push({ path: '/recipes/generate',
        query: { circleId: sheet.value.circleId, dishName } })
    }
  }
}
```

- [ ] **Step 6: 构建验证 + Commit**

Run: `cd web && npm run build`
Expected: 构建成功（产物已输出 `web-dist/` 的话按现有 vite 配置；不改动部署路径）

```bash
git add web/src
git commit -m "feat: add recipe generate/detail and pantry views to web"
```

---

### Task 11: 小样本验证脚本 + compose 冒烟 + 文档收尾

**Files:**
- Create: `scripts/recipe-sample-validation.md`
- Modify: `docs/PROJECT-STATUS.md`（第 4/5/6 节）
- Modify: `docs/TODO.md`（第 3 节勾选 + 随手记录区）

**Interfaces:**
- Consumes: 全部前序任务。
- Produces: 可执行的真实调用验证清单（Key 就绪后人工跑）；文档状态更新。

- [ ] **Step 1: 写小样本验证清单**

`scripts/recipe-sample-validation.md`：

```markdown
# P3 AI 菜谱小样本验证（真实调用，DEEPSEEK_API_KEY 就绪后执行）

前置：`deploy/.env` 已配 `DEEPSEEK_API_KEY`/`DEEPSEEK_MODEL=deepseek-flash`；
`cd deploy && docker compose up -d --build`；`curl http://localhost/api/health` 通过。

## 登录拿 token（小程序测试号环境，任意 openid 走 wx-login 不可行时用 dev 通道——
若仅小程序登录可用，用真机/开发者工具登录一次，从开发者工具 Network 面板复制 accessToken）

TOKEN=<accessToken>
BASE=http://localhost

## 1. 准备：建圈
curl -s -X POST $BASE/api/circles -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"验证圈"}'
# 记下 data.id → CIRCLE_ID

## 2. 调料架约束
curl -s -X POST $BASE/api/me/pantry -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"type":"SEASONING","name":"家传红烧汁"}'

## 3. 生成 3~5 道菜（家常菜 / 带忌口 / 冷门菜各一），逐个观察流式输出：
curl -N -s -X POST $BASE/api/recipes/generate -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"circleId":'"$CIRCLE_ID"',"dishName":"红烧肉"}'

### 检查项（每道菜记录）：
- [ ] delta 流为打字机推进（nginx 不缓冲）
- [ ] 最终 done 事件正常收到
- [ ] GET /api/recipes/{id} 返回结构完整：servings/totalMinutes/ingredients/seasonings/steps/tips
- [ ] 步骤 4~10 步、每步有 durationSec、用量可操作（"2勺""500g"）
- [ ] 调料架中"家传红烧汁"出现在 seasonings（约束生效）
- [ ] content JSON 无围栏/杂质（若解析失败会收到 error 5004 —— 记录原始现象）

## 4. 反馈迭代闭环
curl -s -X POST $BASE/api/recipes/{id}/feedback -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"score":3,"comment":"偏淡了，汤太多"}'
curl -N -s -X POST $BASE/api/recipes/{id}/iterate -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"comment":"做咸一点，收汁"}'

### 检查项：
- [ ] 新版本 version=2，change_note 为提交的 comment
- [ ] 对比 v1/v2 content：反馈点（咸淡/汤汁）被修正，未提及部分基本稳定
- [ ] GET /api/me/taste-profile 出现 summary/tags 且合理

## 5. ai_call_log 抽查
docker exec deploy-mysql-1 mysql -ulinklife -plinklife-prod linklife \
  -e "SELECT scene, ok, prompt_tokens, completion_tokens, user_id FROM ai_call_log ORDER BY id DESC LIMIT 10;"
# [ ] tokens 非空（阻塞调用）；user_id 非 NULL

## 6. 版本上限 / 回滚 / 手动编辑
# [ ] PUT 手动编辑 → v3(MANUAL_EDIT)；POST rollback version=1 → 指针回 1
# [ ] 补齐到 5 版后再迭代 → error 5002

## 结果记录：追加到 docs/TODO.md 随手记录区；prompt 问题现场修正后重跑对应项
```

- [ ] **Step 2: 全量回归**

Run: `cd server && mvn clean test`
Expected: BUILD SUCCESS 全绿

- [ ] **Step 3: 更新 docs/PROJECT-STATUS.md**

- 第 4 节路线图表：P3 行改为"✅ 已完成（2026-09-XX，分支审查后合并）"（日期按实际）。
- 第 5 节已知延后项：删除"`AiGatewayService` 未记 prompt/completion tokens、userId 恒 null"（本阶段已修）；追加"🧑 P3 双端真机流式验证延后（需微信开发者工具真机预览）"。
- 第 6 节"下一步"改写为 P3 完成摘要 + P4 展望（菜谱生成/迭代/流式/自定义能力要点 3~5 句，验证方式引用 `scripts/recipe-sample-validation.md`）。

- [ ] **Step 4: 更新 docs/TODO.md**

- 第 3 节勾选已完成的开发项（数据模型/生成/反馈闭环/自定义/流式/ai_call_log/提示词调优），保留 🧑 项（Key 配置服务器、试吃反馈）与新增 🧑 真机流式验证项。

- [ ] **Step 5: Commit**

```bash
git add scripts/recipe-sample-validation.md docs/PROJECT-STATUS.md docs/TODO.md
git commit -m "docs: record P3 completion and sample validation checklist"
```

---

## Self-Review 记录（写计划时已核对）

1. **Spec 覆盖**：spec §2 数据模型 → Task 1/2；§2.1 content schema → Task 3；§2.2 决策（指针回滚/上限 5/dish upsert/权限/错误码）→ Task 7；§3.1 REST → Task 7/8（by-dish 在 Task 7）；§3.2 调料架/画像 → Task 5/6；§3.3 SSE 协议 → Task 8/9/10；§4 网关 → Task 4；§5 提示词 → Task 8；§6 分层 → Task 5/7/8；§7 双端 → Task 9/10；§8 测试 → 各任务 TDD + Task 11。
2. **无占位符**：所有步骤含实际代码；两处"以真实 schema/API 为准修正"的注记（V1 夹具列名、Spring AI 桩签名）是显式核对指令，非未完成项。
3. **类型一致性**：`streamRequest` 签名双端一致；`RecipeContent`/`IterationResult` 字段在 Task 3/7/8/9/10 间一致；`AiCallLogger.log` 8 参签名在 Task 4 定义、Task 8 测试引用一致；`long[]{recipeId, version}` PersistFn 契约在生成/迭代两侧一致。
