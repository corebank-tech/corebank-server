package com.shinhan.corebank.notification.application.service;

import com.shinhan.corebank.notification.application.port.in.NotificationQueryUseCase;
import com.shinhan.corebank.notification.application.port.out.NotificationQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService implements NotificationQueryUseCase {
    private final NotificationQueryPort notificationQueryPort;

    @Override
    public long getUnreadCount(Long customerId) {
        return notificationQueryPort.countUnread(customerId);
    }
}
