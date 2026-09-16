package com.linklife.auth.dto;

import java.time.LocalDateTime;

public record BindingCodeVO(String code, LocalDateTime expiresAt) {
}
