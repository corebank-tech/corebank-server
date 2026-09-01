package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.TransferHistorySummary;

// 필드명은 api_conventions.md §6-4/§6-6 확정 명칭(failureCount/failureAmount)을 그대로 따른다 - FE가 직접 의존하는 외부 응답 계약
public record TransferHistorySummaryResponse(
        long successCount, long successAmount, long failureCount, long failureAmount) {
    public static TransferHistorySummaryResponse from(TransferHistorySummary summary) {
        return new TransferHistorySummaryResponse(
                summary.successCount(), summary.successAmount(), summary.errorCount(), summary.errorAmount());
    }
}
