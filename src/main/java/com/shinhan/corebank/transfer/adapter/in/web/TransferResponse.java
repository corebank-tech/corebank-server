package com.shinhan.corebank.transfer.adapter.in.web;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;

public record TransferResponse(
        ProcessResultStatus status,
        String transactionNumber,
        LocalDateTime transferredAt,
        String errorCode,
        String errorMessage,
        Long withdrawalBalanceAfter
) {
    public static TransferResponse from(TransferResult result) {
        return new TransferResponse(
                result.status(),
                result.transactionNumber(),
                result.transferredAt(),
                result.errorCode(),
                result.errorMessage(),
                result.withdrawalBalanceAfter()
        );
    }
}
