package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.TransferHistorySummary;

public record TransferHistorySummaryResponse(long successCount, long successAmount, long errorCount, long errorAmount) {
    public static TransferHistorySummaryResponse from(TransferHistorySummary summary) {
        return new TransferHistorySummaryResponse(
                summary.successCount(), summary.successAmount(), summary.errorCount(), summary.errorAmount());
    }
}
