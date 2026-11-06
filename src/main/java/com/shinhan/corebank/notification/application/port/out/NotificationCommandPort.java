package com.shinhan.corebank.notification.application.port.out;

import com.shinhan.corebank.notification.domain.Notification;
import java.time.LocalDateTime;

/** 알림 저장 포트. */
public interface NotificationCommandPort {

    /** 같은 업무(type + refId)의 알림이 없을 때만 넣는다. 이미 있으면 아무것도 바꾸지 않는다. */
    void saveIfAbsent(Notification notification);

    /** 고객 본인의 미읽음 알림일 때만 읽음으로 바꾼다. 이미 읽었거나 조건이 안 맞으면 아무것도 바꾸지 않는다. */
    void markAsReadIfUnread(Long notificationId, Long customerId, LocalDateTime readAt);
}
