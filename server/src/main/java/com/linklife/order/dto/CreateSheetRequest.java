package com.linklife.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateSheetRequest(
        @NotNull Long circleId,
        @NotBlank @Size(max = 64) String title,
        @NotEmpty @Valid List<ItemInput> items) {
}
