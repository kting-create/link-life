package com.linklife.recipe.dto;

import jakarta.validation.constraints.NotNull;

public record RollbackRequest(@NotNull Integer version) {
}
