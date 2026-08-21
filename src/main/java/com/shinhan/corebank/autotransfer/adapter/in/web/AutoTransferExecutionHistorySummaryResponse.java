package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistorySummary;

import io.swagger.v3.oas.annotations.media.Schema;

// 목록 상단 집계를 API 응답으로 감싼 DTO
public record AutoTransferExecutionHistorySummaryResponse(
        @Schema(description = "조회 기간 내 성공 건수")
        long successCount,
        @Schema(description = "조회 기간 내 성공 금액 합계")
        long successAmount,
        @Schema(description = "조회 기간 내 실패 건수")
        long errorCount,
        @Schema(description = "조회 기간 내 실패 금액 합계")
        long errorAmount) {
    public static AutoTransferExecutionHistorySummaryResponse from(AutoTransferExecutionHistorySummary summary) {
        return new AutoTransferExecutionHistorySummaryResponse(summary.successCount(), summary.successAmount(),
                summary.errorCount(), summary.errorAmount());
    }
}
