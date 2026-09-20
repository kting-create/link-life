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
        validate(ErrorCode.RECIPE_PARSE_FAILED);
    }

    public void validate(ErrorCode code) {
        if (ingredients == null || ingredients.isEmpty() || steps == null || steps.isEmpty()) {
            throw new BusinessException(code);
        }
    }
}
