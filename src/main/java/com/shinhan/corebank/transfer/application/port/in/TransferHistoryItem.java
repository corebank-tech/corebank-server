package com.shinhan.corebank.transfer.application.port.in;

import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import java.time.LocalDateTime;

// errorCode/errorMessage는 status=ERROR일 때만 채워짐(TransferResponse와 동일 규칙)
public record TransferHistoryItem(
        String historyId,
        Long transferHoldId,
        String transactionNumber,
        TransferHistoryStatus status,
        LocalDateTime transferredAt,
        Long withdrawalAccountId,
        String depositAccountNumber,
        String payeeName,
        long amount,
        TransferType transferType,
        TransferChannel channel,
        String errorCode,
        String errorMessage) {}
