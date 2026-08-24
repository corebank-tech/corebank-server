package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelResult;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ScheduledTransferCancelSummaryResponse(
        @Schema(description = "취소 성공 건수")
        int successCount,
        @Schema(description = "취소 실패 건수")
        int failureCount) {

    public static ScheduledTransferCancelSummaryResponse from(List<ScheduledTransferCancelResult> results) {
        int successCount = (int) results.stream()
                .filter(result -> result.status() == ProcessResultStatus.SUCCESS)
                .count();
        return new ScheduledTransferCancelSummaryResponse(successCount, results.size() - successCount);
    }
}
