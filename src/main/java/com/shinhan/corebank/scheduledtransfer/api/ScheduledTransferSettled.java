package com.shinhan.corebank.scheduledtransfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationRequestCarrier;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.Builder;

/**
 * 예약이체 한 건이 SUCCESS 또는 ERROR 로 확정됐다. 정상 확정 · 재확정마다 나간다(#707).
 * refId 는 예약 건 ID(scheduled_transfer_id)이고, 고객 알림은 notificationRequest 로 싣는다. 재확정 건은 errorCode 가
 * 없다. 고객 취소는 확정이 아니라 포함하지 않는다(#394).
 */
@Builder
public record ScheduledTransferSettled(
        Long customerId,
        Long refId,
        ProcessResultStatus status,
        String errorCode,
        LocalDateTime occurredAt,
        NotificationRequest notificationRequest)
        implements DomainEvent, NotificationRequestCarrier {

    @Override
    public Optional<NotificationRequest> notification() {
        return Optional.ofNullable(notificationRequest);
    }
}
