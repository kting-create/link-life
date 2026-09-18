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
