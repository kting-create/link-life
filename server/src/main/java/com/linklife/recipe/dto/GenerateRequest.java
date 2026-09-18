package com.linklife.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GenerateRequest(
        @NotNull Long circleId,
        @NotBlank @Size(max = 64) String dishName) {
}
