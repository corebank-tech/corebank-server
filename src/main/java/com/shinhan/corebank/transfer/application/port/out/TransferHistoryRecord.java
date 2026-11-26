package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.application.port.in.TransferHistoryStatus;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import java.time.LocalDateTime;

/** transfer와 transfer_hold를 합친 고객 이체결과조회용 읽기 모델. */
public record TransferHistoryRecord(
        String historyId,
        Long transferHoldId,
        String transactionNumber,
        TransferHistoryStatus status,
        LocalDateTime occurredAt,
        Long withdrawalAccountId,
        String depositAccountNumber,
        String payeeName,
        long amount,
        TransferType transferType,
        TransferChannel channel,
        String errorCode,
        String errorMessage) {}
