package com.shinhan.corebank.notification.application.service;

import com.shinhan.corebank.common.util.PageableResolver;
import com.shinhan.corebank.notification.application.port.in.NotificationQueryUseCase;
import com.shinhan.corebank.notification.application.port.in.dto.NotificationListResult;
import com.shinhan.corebank.notification.application.port.out.NotificationQueryPort;
import com.shinhan.corebank.notification.domain.NotificationInboxEntry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService implements NotificationQueryUseCase {

    private static final Set<Integer> ALLOWED_PAGE_SIZE = Set.of(5, 10, 20, 30, 50);

    private final NotificationQueryPort notificationQueryPort;
    private final Clock clock;

    @Override
    public NotificationListResult getNotifications(Long customerId, int page, int size, boolean all) {
        final Pageable pageable = PageableResolver.resolve(page, size, all, ALLOWED_PAGE_SIZE);
        final LocalDateTime asOf = LocalDateTime.now(clock);
        final Page<NotificationInboxEntry> notifications = notificationQueryPort.findInbox(customerId, pageable);

        return NotificationListResult.builder()
                .asOf(asOf)
                .unreadCount(notificationQueryPort.countUnread(customerId))
                .notifications(notifications)
                .build();
    }

    @Override
    public long getUnreadCount(Long customerId) {
        return notificationQueryPort.countUnread(customerId);
    }
}
