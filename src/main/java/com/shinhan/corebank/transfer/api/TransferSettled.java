package com.shinhan.corebank.transfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 이체 한 건이 SUCCESS 또는 ERROR 로 확정됐다. 즉시·예약·자동이체 모두 이 이벤트이고 txType 으로 구분한다
 * (IMMEDIATE_TRANSFER · SCHEDULED_TRANSFER · AUTO_TRANSFER, LedgerPostingContext 와 같은 값). refId 는
 * transfer_id. transfer 행이 생기기 전의 사전검증 실패는 발행하지 않는다 — 즉시이체는 고객이 API 응답으로
 * 이미 오류를 받고, 예약·자동이체는 배치가 자기 이벤트로 따로 발행한다.
 *
 * <p>구독자가 transfer 를 다시 읽지 않도록 알림 문구에 필요한 값을 담는다. counterpartyName 은 발행 시점에
 * 마스킹한 값이고, 전체 계좌번호·인증 토큰은 넣지 않는다.
 */
@Builder
public record TransferSettled(
        Long customerId,
        Long refId,
        String txType,
        ProcessResultStatus status,
        String errorCode,
        LocalDateTime occurredAt,
        long amount,
        String counterpartyName)
        implements DomainEvent {}
