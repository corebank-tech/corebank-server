package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

// errorCode/errorMessage는 status=ERROR일 때만 채워짐(TransferResponse와 동일 규칙)
public record TransferHistoryItem(
        String transactionNumber,
        ProcessResultStatus status,
        LocalDateTime transferredAt,
        Long withdrawalAccountId,
        String depositAccountNumber,
        String payeeName,
        long amount,
        TransferType transferType,
        TransferChannel channel,
        String errorCode,
        String errorMessage
) {
}
