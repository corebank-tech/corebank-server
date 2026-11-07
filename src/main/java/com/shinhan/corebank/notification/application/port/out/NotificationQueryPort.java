package com.shinhan.corebank.notification.application.port.out;

import com.shinhan.corebank.notification.domain.NotificationInboxEntry;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 저장된 알림 조회 포트. */
public interface NotificationQueryPort {

    /** 고객의 알림을 occurredAt DESC, notificationId DESC 로 고정해 조회한다. */
    Page<NotificationInboxEntry> findInbox(Long customerId, Pageable pageable);

    long countUnread(Long customerId);

    Optional<NotificationInboxEntry> findById(Long notificationId);
}
