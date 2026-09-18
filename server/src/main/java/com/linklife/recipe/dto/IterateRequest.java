package com.linklife.recipe.dto;

import jakarta.validation.constraints.Size;

public record IterateRequest(@Size(max = 512) String comment) {
}
