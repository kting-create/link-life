package com.linklife.recipe.dto;

import jakarta.validation.constraints.Size;

public record EditRecipeRequest(
        RecipeContent content,
        @Size(max = 64) String customName,
        @Size(max = 255) String changeNote) {
}
