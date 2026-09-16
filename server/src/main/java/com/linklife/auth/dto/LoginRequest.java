package com.linklife.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String code) {
}
