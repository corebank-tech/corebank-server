package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import lombok.Builder;

@Builder
public record TransferResult(
    ProcessResultStatus status,
    String transactionNumber,
    LocalDateTime transferredAt,
    String errorCode,
    String errorMessage,
    Long withdrawalBalanceAfter
) {
}
