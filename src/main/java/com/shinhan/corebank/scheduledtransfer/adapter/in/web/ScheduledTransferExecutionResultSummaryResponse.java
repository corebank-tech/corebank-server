package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultSummary;

public record ScheduledTransferExecutionResultSummaryResponse(long successCount,
                                                               long successAmount,
                                                               long failedCount,
                                                               long failedAmount,
                                                               long canceledCount,
                                                               long canceledAmount) {
    public static ScheduledTransferExecutionResultSummaryResponse from(ScheduledTransferExecutionResultSummary summary) {
        return new ScheduledTransferExecutionResultSummaryResponse(
                summary.successCount(), summary.successAmount(),
                summary.failedCount(), summary.failedAmount(),
                summary.canceledCount(), summary.canceledAmount());
    }
}
