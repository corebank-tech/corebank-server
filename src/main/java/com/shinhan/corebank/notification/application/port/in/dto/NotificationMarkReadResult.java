package com.shinhan.corebank.notification.application.port.in.dto;

import com.shinhan.corebank.notification.domain.NotificationInboxEntry;
import java.time.LocalDateTime;
import lombok.Builder;

/** 읽음 처리 결과. readAt 은 최초 읽음 시각이고, unreadCount 는 처리 후 남은 미읽음 건수다. */
@Builder
public record NotificationMarkReadResult(Long notificationId, LocalDateTime readAt, long unreadCount) {

    public static NotificationMarkReadResult from(NotificationInboxEntry notification, long unreadCount) {
        return NotificationMarkReadResult.builder()
                .notificationId(notification.notificationId())
                .readAt(notification.readAt())
                .unreadCount(unreadCount)
                .build();
    }
}
