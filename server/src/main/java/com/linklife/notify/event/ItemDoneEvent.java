package com.linklife.notify.event;

public record ItemDoneEvent(long sheetId, String sheetTitle, long itemId,
                            String dishName, long claimantId, long creatorId) {
}
