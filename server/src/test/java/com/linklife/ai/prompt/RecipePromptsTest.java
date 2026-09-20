package com.linklife.ai.prompt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linklife.recipe.dto.RecipeContent;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipePromptsTest {

    @Test
    void iterateIncludesTasteSummaryWhenPresent() {
        RecipeContent content = new RecipeContent(2, 30,
                List.of(), List.of(), List.of(), null);
        String prompt = RecipePrompts.iterate("番茄炒蛋", content, List.of(), "不吃香菜");
        assertTrue(prompt.contains("口味画像"));
        assertTrue(prompt.contains("不吃香菜"));
    }

    @Test
    void iterateOmitsTasteSummaryWhenNull() {
        RecipeContent content = new RecipeContent(2, 30,
                List.of(), List.of(), List.of(), null);
        String prompt = RecipePrompts.iterate("番茄炒蛋", content, List.of(), null);
        assertFalse(prompt.contains("口味画像"));
    }
}
