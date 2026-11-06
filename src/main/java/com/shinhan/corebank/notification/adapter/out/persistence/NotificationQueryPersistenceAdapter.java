package com.shinhan.corebank.notification.adapter.out.persistence;

import com.shinhan.corebank.notification.application.port.out.NotificationQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationQueryPersistenceAdapter implements NotificationQueryPort {

    private final NotificationJpaRepository notificationRepository;

    @Override
    public long countUnread(Long customerId) {
        return notificationRepository.countByCustomerIdAndIsReadFalse(customerId);
    }
}
