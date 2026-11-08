package com.shinhan.corebank.notification.domain;

import com.shinhan.corebank.notification.api.NotificationType;
import java.time.LocalDateTime;
import lombok.Builder;

/** 알림함에 보이는 저장된 알림 한 건. 미읽음이면 readAt 이 null 이다. */
@Builder(toBuilder = true)
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

    /** 미읽음 알림을 주어진 시각에 읽은 상태로 바꾼 사본. 이미 읽은 알림은 최초 readAt 을 지키려고 그대로 돌려준다. */
    public NotificationInboxEntry markAsRead(LocalDateTime now) {
        if (isRead) {
            return this;
        }
        return toBuilder().isRead(true).readAt(now).build();
    }
}
