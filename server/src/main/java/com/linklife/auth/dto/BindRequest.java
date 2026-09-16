package com.linklife.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record BindRequest(@NotBlank String code) {
}
