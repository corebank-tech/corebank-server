package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultSummary;
import io.swagger.v3.oas.annotations.media.Schema;

// 필드명은 api_conventions.md §6-4/§6-6 확정 명칭(failureCount/failureAmount)을 그대로 따른다 - FE가 직접 의존하는 외부 응답 계약
public record ScheduledTransferExecutionResultSummaryResponse(
        @Schema(description = "조회 기간 내 정상처리 건수") long successCount,
        @Schema(description = "조회 기간 내 정상처리 금액 합계") long successAmount,
        @Schema(description = "조회 기간 내 오류처리 건수") long failureCount,
        @Schema(description = "조회 기간 내 오류처리 금액 합계") long failureAmount,
        @Schema(description = "조회 기간 내 취소 건수") long canceledCount,
        @Schema(description = "조회 기간 내 취소 금액 합계") long canceledAmount) {
    public static ScheduledTransferExecutionResultSummaryResponse from(
            ScheduledTransferExecutionResultSummary summary) {
        return new ScheduledTransferExecutionResultSummaryResponse(
                summary.successCount(), summary.successAmount(),
                summary.failedCount(), summary.failedAmount(),
                summary.canceledCount(), summary.canceledAmount());
    }
}
