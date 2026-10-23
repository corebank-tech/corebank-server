package com.shinhan.corebank.autotransfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 자동이체 한 회차가 SUCCESS 또는 ERROR 로 확정됐다. 배치의 정상 확정과 재확정이 결과와 상관없이 확정마다
 * 낸다(#707). refId 는 등록 ID 가 아니라 회차의 execution_id 다(#395). 이체 엔진의 TransferSettled 는 refId 가
 * transfer_id 라, 회차 기준으로 받아야 하는 알림은 이 이벤트를 구독한다. 재확정 건은 errorCode 가 없고, 정상
 * 확정과 재확정이 겹치면 같은 회차로 두 번 나갈 수 있다 — 구독자는 업무 키로 멱등해야 한다.
 */
@Builder
public record AutoTransferExecutionSettled(
        Long customerId,
        Long refId,
        ProcessResultStatus status,
        String errorCode,
        LocalDateTime occurredAt,
        long amount,
        String counterpartyName)
        implements DomainEvent {}
