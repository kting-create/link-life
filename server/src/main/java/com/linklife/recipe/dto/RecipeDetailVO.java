package com.linklife.recipe.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RecipeDetailVO(long id, long dishId, String dishName, String customName,
                             int currentVersion, RecipeContent content,
                             List<VersionMetaVO> versions, LocalDateTime updatedAt) {
}
