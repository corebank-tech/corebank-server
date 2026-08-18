package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistorySummary;

// 목록 상단 집계를 API 응답으로 감싼 DTO
public record AutoTransferExecutionHistorySummaryResponse(long successCount, long successAmount, long errorCount, long errorAmount) {
    public static AutoTransferExecutionHistorySummaryResponse from(AutoTransferExecutionHistorySummary summary) {
        return new AutoTransferExecutionHistorySummaryResponse(summary.successCount(), summary.successAmount(),
                summary.errorCount(), summary.errorAmount());
    }
}
