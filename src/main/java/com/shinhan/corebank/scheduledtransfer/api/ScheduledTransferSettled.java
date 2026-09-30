package com.shinhan.corebank.scheduledtransfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 예약이체가 transfer 행이 생기기 전의 사전검증에서 ERROR 로 확정됐다. refId 는 scheduled_transfer_id.
 * transfer 행이 생긴 성공·실패는 이체 엔진이 TransferSettled 로 발행하므로 이 이벤트로 나가지 않는다. 고객
 * 취소는 별도 확정이 아니므로 여기 포함하지 않는다(#394).
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
