package com.shinhan.corebank.transfer.api;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEvent;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 즉시이체가 SUCCESS 또는 ERROR 로 확정됐다. refId 는 transfer_id. 락 획득 전 실패(수취인 없음·OTP 실패)는
 * transfer 행이 없어 발행하지 않는다 — 고객은 API 응답으로 이미 오류를 받았다.
 *
 * <p>구독자가 transfer 를 다시 읽지 않도록 알림 문구에 필요한 값을 담는다. counterpartyName 은 발행 시점에
 * 마스킹한 값이고, 전체 계좌번호·인증 토큰은 넣지 않는다.
 */
@Builder
public record TransferSettled(
        Long customerId,
        Long refId,
        ProcessResultStatus status,
        String errorCode,
        LocalDateTime occurredAt,
        long amount,
        String counterpartyName)
        implements DomainEvent {}
