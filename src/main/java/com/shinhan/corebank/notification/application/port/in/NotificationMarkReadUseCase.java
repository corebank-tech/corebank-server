package com.shinhan.corebank.notification.application.port.in;

import com.shinhan.corebank.notification.application.port.in.dto.NotificationMarkReadResult;

/** 고객 본인의 알림을 읽음으로 표시한다. */
public interface NotificationMarkReadUseCase {

    /** 이미 읽은 알림도 성공이고 최초 읽음 시각을 유지한다. 없는 알림은 NTF0201, 타 고객 알림은 CMN0102 다. */
    NotificationMarkReadResult markAsRead(Long customerId, Long notificationId);
}
