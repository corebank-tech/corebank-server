package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

// withdrawalBalanceAfter/errorCode/errorMessage는 TransferResponse와 동일하게 상태에 따라 배타적으로 채워짐
public record TransferHistoryDetail(
        String transactionNumber,
        ProcessResultStatus status,
        Long withdrawalAccountId,
        Long depositAccountId,
        String depositAccountNumber,
        String payeeName,
        long amount,
        long fee,
        TransferType transferType,
        TransferChannel channel,
        String myPassbookMemo,
        String recipientPassbookMemo,
        Long withdrawalBalanceAfter,
        String errorCode,
        String errorMessage,
        LocalDateTime transferredAt
) {
}
