package com.shinhan.corebank.notification.domain;

import com.shinhan.corebank.notification.api.NotificationType;
import java.time.LocalDateTime;
import lombok.Builder;

/** 알림함에 보이는 저장된 알림 한 건. 미읽음이면 readAt 이 null 이다. */
@Builder
public record NotificationInboxEntry(
        Long notificationId,
        Long customerId,
        NotificationType type,
        String title,
        String content,
        boolean isRead,
        LocalDateTime readAt,
        LocalDateTime occurredAt) {

    public boolean isOwnedBy(Long requestingCustomerId) {
        return customerId.equals(requestingCustomerId);
    }
}
