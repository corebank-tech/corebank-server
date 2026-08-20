package com.shinhan.corebank.transfer.adapter.in.web;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryDetail;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

public record TransferHistoryDetailResponse(
        String transactionNumber,
        ProcessResultStatus status,
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
    public static TransferHistoryDetailResponse from(TransferHistoryDetail detail) {
        return new TransferHistoryDetailResponse(
                detail.transactionNumber(),
                detail.status(),
                MaskingUtil.maskAccountNumber(detail.depositAccountNumber()),
                MaskingUtil.maskName(detail.payeeName()),
                detail.amount(),
                detail.fee(),
                detail.transferType(),
                detail.channel(),
                detail.myPassbookMemo(),
                detail.recipientPassbookMemo(),
                detail.withdrawalBalanceAfter(),
                detail.errorCode(),
                detail.errorMessage(),
                detail.transferredAt()
        );
    }
}
