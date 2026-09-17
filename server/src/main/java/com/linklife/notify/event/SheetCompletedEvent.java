package com.linklife.notify.event;

public record SheetCompletedEvent(long circleId, long sheetId, String title, long creatorId) {
}
