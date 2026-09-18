package com.linklife.recipe.dto;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import java.util.List;

public record PhotoAnalysis(String advice, List<Change> changes) {

    public record Change(Integer stepNo, String text, Integer durationSec) {
    }

    public void validate() {
        if (advice == null || advice.isBlank()) {
            throw new BusinessException(ErrorCode.VISION_AI_FAILED);
        }
    }

    public List<Change> validChanges() {
        return changes == null ? List.of() : changes;
    }
}
