package com.shinhan.corebank.autotransfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import java.time.LocalDateTime;
import lombok.Builder;

/** 자동이체 한 회차가 SUCCESS 또는 ERROR 로 확정됐다. refId 는 등록 ID 가 아니라 회차의 execution_id 다(#395). */
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
