package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultItem;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import java.time.LocalDateTime;

// executedAt은 실제 실행(확정)시각(api_conventions.md §6-4) - CANCELED 건은 실행된 적이 없어 null
public record ScheduledTransferExecutionResultItemResponse(Long scheduledTransferId,
                                                            ScheduledTransferStatus status,
                                                            LocalDateTime executedAt,
                                                            String withdrawalAccountNumber,
                                                            String accountNumber,
                                                            String payeeName,
                                                            Long amount,
                                                            String transactionNumber,
                                                            String failureReason) {
    public static ScheduledTransferExecutionResultItemResponse from(ScheduledTransferExecutionResultItem item) {
        return new ScheduledTransferExecutionResultItemResponse(
                item.scheduledTransferId(),
                item.status(),
                item.executedAt(),
                MaskingUtil.maskAccountNumber(item.withdrawalAccountNumber()),
                MaskingUtil.maskAccountNumber(item.payeeAccountNumber()),
                MaskingUtil.maskName(item.payeeName()),
                item.amount(),
                item.transactionNumber(),
                item.failureReason()
        );
    }
}
