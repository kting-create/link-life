package com.linklife.recipe.dto;

import java.time.LocalDateTime;

public record RecipeVersionVO(int version, String source, String changeNote,
                              RecipeContent content, LocalDateTime createdAt) {
}
