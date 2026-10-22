package com.shinhan.corebank.notification.adapter.out.persistence;

import com.shinhan.corebank.notification.application.port.out.NotificationCommandPort;
import com.shinhan.corebank.notification.domain.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationPersistenceAdapter implements NotificationCommandPort {

    private final NotificationJpaRepository notificationRepository;

    @Override
    public void saveIfAbsent(Notification notification) {
        notificationRepository.insertIfAbsent(
                notification.customerId(),
                notification.type().name(),
                notification.refId(),
                notification.title(),
                notification.content(),
                notification.occurredAt());
    }
}
