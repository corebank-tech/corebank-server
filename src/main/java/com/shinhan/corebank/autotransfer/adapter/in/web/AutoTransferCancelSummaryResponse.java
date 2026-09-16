package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferCancelResult;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AutoTransferCancelSummaryResponse(
        @Schema(description = "해지 성공 건수") int successCount, @Schema(description = "해지 실패 건수") int failureCount) {

    public static AutoTransferCancelSummaryResponse from(List<AutoTransferCancelResult> results) {
        int successCount = (int) results.stream()
                .filter(result -> result.status() == ProcessResultStatus.SUCCESS)
                .count();
        return new AutoTransferCancelSummaryResponse(successCount, results.size() - successCount);
    }
}
