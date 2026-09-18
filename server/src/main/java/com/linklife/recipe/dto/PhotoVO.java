package com.linklife.recipe.dto;

import java.time.LocalDateTime;

public record PhotoVO(Long id, Integer stepNo, String url, Long sizeBytes, Long uploaderId,
                      boolean analyzed, LocalDateTime createdAt) {
}
