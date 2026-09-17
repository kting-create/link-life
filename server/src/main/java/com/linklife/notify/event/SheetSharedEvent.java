package com.linklife.notify.event;

public record SheetSharedEvent(long circleId, long sheetId, String title,
                               long creatorId, int itemCount) {
}
