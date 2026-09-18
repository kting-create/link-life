package com.linklife.recipe.dto;

import java.time.LocalDateTime;

public record VersionMetaVO(int version, String source, String changeNote,
                            Long createdBy, LocalDateTime createdAt) {
}
