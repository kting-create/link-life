package com.linklife.notify.dto;

public record NotificationVO(long id, String type, String title, String content,
                             Long sheetId, Long circleId, boolean read, String createdAt) {
}
