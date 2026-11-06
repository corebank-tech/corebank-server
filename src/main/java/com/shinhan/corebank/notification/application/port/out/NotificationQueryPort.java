package com.shinhan.corebank.notification.application.port.out;

/** 저장된 알림 조회 포트. */
public interface NotificationQueryPort {
    long countUnread(Long customerId);
}
