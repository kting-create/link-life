package com.linklife.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ItemInput(
        @NotBlank @Size(max = 64) String dishName,
        @Size(max = 255) String note) {
}
