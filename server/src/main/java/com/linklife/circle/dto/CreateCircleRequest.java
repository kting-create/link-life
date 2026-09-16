package com.linklife.circle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCircleRequest(@NotBlank @Size(max = 64) String name) {
}
