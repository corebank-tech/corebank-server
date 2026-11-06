package com.shinhan.corebank.notification.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.notification.application.port.in.NotificationMarkReadUseCase;
import com.shinhan.corebank.notification.application.port.in.dto.NotificationMarkReadResult;
import com.shinhan.corebank.notification.application.port.out.NotificationCommandPort;
import com.shinhan.corebank.notification.application.port.out.NotificationQueryPort;
import com.shinhan.corebank.notification.domain.NotificationInboxEntry;
import com.shinhan.corebank.notification.domain.exception.NotificationErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationMarkReadService implements NotificationMarkReadUseCase {

    private final NotificationCommandPort notificationCommandPort;
    private final NotificationQueryPort notificationQueryPort;
    private final Clock clock;

    @Override
    public NotificationMarkReadResult markAsRead(Long customerId, Long notificationId) {
        // UPDATE 를 먼저 해야 뒤 SELECT 의 스냅샷이 경합 상대의 커밋 뒤에 잡혀 최초 readAt 을 읽는다
        notificationCommandPort.markAsReadIfUnread(notificationId, customerId, LocalDateTime.now(clock));
        final NotificationInboxEntry notification = notificationQueryPort
                .findById(notificationId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isOwnedBy(customerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        return NotificationMarkReadResult.builder()
                .notificationId(notificationId)
                .readAt(notification.readAt())
                .unreadCount(notificationQueryPort.countUnread(customerId))
                .build();
    }
}
