package com.linklife.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateItemStatusRequest(
        @NotBlank @Pattern(regexp = "COOKING|DONE") String itemStatus) {
}
