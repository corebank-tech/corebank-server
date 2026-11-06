package com.shinhan.corebank.notification.application.port.in;

/** 고객 본인의 알림함을 조회한다. */
public interface NotificationQueryUseCase {
    long getUnreadCount(Long customerId);
}
