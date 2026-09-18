package com.linklife.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PantryAddRequest(
        @Pattern(regexp = "SEASONING|INGREDIENT", message = "类型无效") String type,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 128) String note) {
}
