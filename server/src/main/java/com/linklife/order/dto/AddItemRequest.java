package com.linklife.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddItemRequest(
        @NotNull Long sheetId,
        @NotBlank @Size(max = 64) String dishName,
        @Size(max = 255) String note) {
}
