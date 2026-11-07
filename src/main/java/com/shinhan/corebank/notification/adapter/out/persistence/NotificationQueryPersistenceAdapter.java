package com.shinhan.corebank.notification.adapter.out.persistence;

import com.shinhan.corebank.notification.application.port.out.NotificationQueryPort;
import com.shinhan.corebank.notification.domain.NotificationInboxEntry;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationQueryPersistenceAdapter implements NotificationQueryPort {

    private final NotificationJpaRepository notificationRepository;

    @Override
    public Page<NotificationInboxEntry> findInbox(Long customerId, Pageable pageable) {
        return notificationRepository
                .findByCustomerIdOrderByOccurredAtDescNotificationIdDesc(customerId, pageable)
                .map(NotificationJpaEntity::toDomain);
    }

    @Override
    public long countUnread(Long customerId) {
        return notificationRepository.countByCustomerIdAndIsReadFalse(customerId);
    }

    @Override
    public Optional<NotificationInboxEntry> findById(Long notificationId) {
        return notificationRepository.findById(notificationId).map(NotificationJpaEntity::toDomain);
    }
}
