package com.shinhan.corebank.notification.application.port.out;

import com.shinhan.corebank.notification.domain.Notification;
import com.shinhan.corebank.notification.domain.NotificationInboxEntry;
import java.time.LocalDateTime;
import java.util.Optional;

/** 알림 저장 포트. */
public interface NotificationCommandPort {

    /** 같은 업무(type + refId)의 알림이 없을 때만 넣는다. 이미 있으면 아무것도 바꾸지 않는다. */
    void saveIfAbsent(Notification notification);

    /** 알림 한 건을 행 잠금으로 읽는다. 동시에 읽음 처리하는 다른 요청은 이 트랜잭션이 끝날 때까지 기다린다. */
    Optional<NotificationInboxEntry> findByIdForUpdate(Long notificationId);

    void markAsRead(Long notificationId, LocalDateTime readAt);
}
