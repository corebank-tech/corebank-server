package com.shinhan.corebank.account.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 만기 감지 COB에서 계좌가 ACTIVE에서 MATURED로 전이되었을 때 발행한다.
 *
 * <p>refId는 account_id이며 notification.ref_id 유형에는 아직 없다.
 * 상태 전이 기준일은 실제 계약 만기일보다 앞설 수 있다.
 * 이번 단계에서는 알림 소비자가 없다.
 */
@Builder
public record AccountMatured(
        Long customerId,
        Long refId,
        ProcessResultStatus status,
        String errorCode,
        LocalDateTime occurredAt,
        Long accountId,
        LocalDate maturityDate,
        LocalDate maturityBaseDate)
        implements DomainEvent {}
