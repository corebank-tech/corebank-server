package com.shinhan.corebank.scheduledtransfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 예약이체가 SUCCESS 또는 ERROR 로 확정됐다. refId 는 scheduled_transfer_id. 고객 취소는 status 로
 * 구분하지 않고 별도 확정이 아니므로 여기 포함하지 않는다(#394).
 */
@Builder
public record ScheduledTransferSettled(
        Long customerId,
        Long refId,
        ProcessResultStatus status,
        String errorCode,
        LocalDateTime occurredAt,
        long amount,
        String counterpartyName)
        implements DomainEvent {}
