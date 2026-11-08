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
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional
public class NotificationMarkReadService implements NotificationMarkReadUseCase {

    private final NotificationCommandPort notificationCommandPort;
    private final NotificationQueryPort notificationQueryPort;
    private final Clock clock;

    @Override
    public NotificationMarkReadResult markAsRead(Long customerId, Long notificationId) {
        // 일반 SELECT 는 REPEATABLE READ 스냅샷이라 경합 상대의 커밋을 못 보므로 잠금 읽기로 최신 행을 본다
        final NotificationInboxEntry notification = notificationCommandPort
                .findByIdForUpdate(notificationId)
                .orElseThrow(() -> new BusinessException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        if (!notification.isOwnedBy(customerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        final NotificationInboxEntry readNotification = notification.markAsRead(LocalDateTime.now(clock));
        if (!notification.isRead()) {
            notificationCommandPort.markAsRead(notificationId, readNotification.readAt());
        }
        return NotificationMarkReadResult.from(readNotification, notificationQueryPort.countUnread(customerId));
    }
}
