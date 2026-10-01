package com.shinhan.corebank.autotransfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 자동이체 한 회차가 transfer 행 없이 ERROR 로 확정됐다 — 사전검증 실패이거나, 재확정 배치가 transfer 행을
 * 찾지 못한 경우다. refId 는 등록 ID 가 아니라 회차의 execution_id 다(#395). transfer 행이 생긴 성공·실패는
 * 이체 엔진이 TransferSettled 로 발행한다.
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
