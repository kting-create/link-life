package com.linklife.circle.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinCircleRequest(@NotBlank String inviteCode) {
}
