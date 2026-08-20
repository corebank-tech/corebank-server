package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultSummary;

// 필드명은 api_conventions.md §6-4/§6-6 확정 명칭(failureCount/failureAmount)을 그대로 따른다 - FE가 직접 의존하는 외부 응답 계약
public record ScheduledTransferExecutionResultSummaryResponse(long successCount,
                                                               long successAmount,
                                                               long failureCount,
                                                               long failureAmount,
                                                               long canceledCount,
                                                               long canceledAmount) {
    public static ScheduledTransferExecutionResultSummaryResponse from(ScheduledTransferExecutionResultSummary summary) {
        return new ScheduledTransferExecutionResultSummaryResponse(
                summary.successCount(), summary.successAmount(),
                summary.failedCount(), summary.failedAmount(),
                summary.canceledCount(), summary.canceledAmount());
    }
}
