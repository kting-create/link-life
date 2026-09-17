package com.linklife.order.dto;

import java.util.List;

public record SheetDetailVO(
        long id,
        long circleId,
        long creatorId,
        String title,
        String status,
        String shareToken,
        List<ItemVO> items) {
}
