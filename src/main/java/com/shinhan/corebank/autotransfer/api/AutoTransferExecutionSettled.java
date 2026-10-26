package com.shinhan.corebank.autotransfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import com.shinhan.corebank.notification.api.NotificationRequest;
import com.shinhan.corebank.notification.api.NotificationRequestCarrier;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.Builder;

/**
 * 자동이체 회차 하나가 SUCCESS 또는 ERROR 로 확정됐다. 정상 확정 · 재확정마다 나간다(#707).
 * refId 는 회차 ID(execution_id)이고, 고객 알림은 notificationRequest 로 싣는다. 재확정 건은 errorCode 가 없다.
 * 정상 확정과 재확정이 겹치면 두 번 나갈 수 있어 구독자는 업무 키로 멱등해야 한다.
 */
@Builder
public record AutoTransferExecutionSettled(
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
