package com.shinhan.corebank.transfer.application.port.in;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.time.LocalDateTime;
import lombok.Builder;

/**
 * 타행이체 결과. 당행 결과(TransferResult)에 은행코드·예금주를 더한 모양이다(interbank_transfer_fe_handoff.md §5).
 *
 * @param payeeName 상대 은행이 돌려준 예금주 원문. SUCCESS일 때만 있고 마스킹은 응답 경계에서 한다
 */
@Builder
public record InterbankTransferResult(
        ProcessResultStatus status,
        String transactionNumber,
        LocalDateTime transferredAt,
        String payeeBankCode,
        String payeeName,
        Long withdrawalBalanceAfter,
        String errorCode,
        String errorMessage) {}
