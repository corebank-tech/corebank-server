package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.TransferMonthlyStatistics;

// 필드명은 TransferHistorySummaryResponse와 동일 관행(failureCount/failureAmount)을 따른다
public record TransferMonthlyStatisticsResponse(
        String yearMonth, long successCount, long successAmount, long failureCount, long failureAmount) {
    public static TransferMonthlyStatisticsResponse from(TransferMonthlyStatistics statistics) {
        return new TransferMonthlyStatisticsResponse(
                statistics.yearMonth().toString(),
                statistics.successCount(),
                statistics.successAmount(),
                statistics.errorCount(),
                statistics.errorAmount());
    }
}
