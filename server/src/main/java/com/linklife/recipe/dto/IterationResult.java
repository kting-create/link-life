package com.linklife.recipe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.user.dto.TasteSummary;

public record IterationResult(RecipeContent recipe,
                              @JsonProperty("taste_summary") TasteSummary tasteSummary) {

    public void validate() {
        if (recipe == null) {
            throw new BusinessException(ErrorCode.RECIPE_PARSE_FAILED);
        }
        recipe.validate();
    }
}
