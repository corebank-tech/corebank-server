package com.shinhan.corebank.transfer.adapter.in.web;

import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryItem;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

// 필드명은 api_conventions.md §6-2/§6-4 확정 명칭(executedAt/accountNumber/failureReason)을 따른다 - FE가 직접 의존하는 외부 응답 계약
public record TransferHistoryItemResponse(
        String transactionNumber,
        ProcessResultStatus status,
        LocalDateTime executedAt,
        String accountNumber,
        String payeeName,
        long amount,
        TransferType transferType,
        TransferChannel channel,
        String errorCode,
        String failureReason
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
