package com.shinhan.corebank.scheduledtransfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 예약이체 한 건이 SUCCESS 또는 ERROR 로 확정됐다. 배치의 정상 확정과 재확정이 결과와 상관없이 확정마다
 * 낸다(#707). refId 는 scheduled_transfer_id. 이체 엔진의 TransferSettled 는 refId 가 transfer_id 라, 예약 건
 * 기준으로 받아야 하는 알림은 이 이벤트를 구독한다. 재확정 건은 errorCode 가 없다. 고객 취소는 별도 확정이
 * 아니므로 여기 포함하지 않는다(#394).
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
