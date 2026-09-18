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
