package com.linklife.notify.dto;

import java.util.List;

public record NotificationPageVO(List<NotificationVO> items, long unreadCount) {
}
