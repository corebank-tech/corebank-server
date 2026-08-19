package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultItem;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;

import java.time.LocalDate;

public record ScheduledTransferExecutionResultItemResponse(Long scheduledTransferId,
                                                            ScheduledTransferStatus status,
                                                            LocalDate scheduledDate,
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
                item.scheduledDate(),
                MaskingUtil.maskAccountNumber(item.withdrawalAccountNumber()),
                MaskingUtil.maskAccountNumber(item.payeeAccountNumber()),
                MaskingUtil.maskName(item.payeeName()),
                item.amount(),
                item.transactionNumber(),
                item.failureReason()
        );
    }
}
