package com.linklife.order.dto;

public record ItemVO(
        long id,
        String dishName,
        String note,
        Long claimantId,
        String claimantNickname,
        String itemStatus) {
}
