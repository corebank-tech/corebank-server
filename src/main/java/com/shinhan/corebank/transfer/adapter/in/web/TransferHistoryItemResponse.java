package com.shinhan.corebank.transfer.adapter.in.web;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryItem;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

public record TransferHistoryItemResponse(
        String transactionNumber,
        ProcessResultStatus status,
        LocalDateTime transferredAt,
        String depositAccountNumber,
        String payeeName,
        long amount,
        TransferType transferType,
        TransferChannel channel,
        String errorCode,
        String errorMessage
) {
    public static TransferHistoryItemResponse from(TransferHistoryItem item) {
        return new TransferHistoryItemResponse(
                item.transactionNumber(),
                item.status(),
                item.transferredAt(),
                MaskingUtil.maskAccountNumber(item.depositAccountNumber()),
                MaskingUtil.maskName(item.payeeName()),
                item.amount(),
                item.transferType(),
                item.channel(),
                item.errorCode(),
                item.errorMessage()
        );
    }
}
