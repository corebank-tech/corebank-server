package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ScheduledTransferCancelResponse(
        @Schema(description = "취소 성공·실패 건수 집계") ScheduledTransferCancelSummaryResponse summary,
        @Schema(description = "요청한 ID 순서(오름차순)대로의 건별 처리결과") List<ScheduledTransferCancelItemResponse> items) {

    public static ScheduledTransferCancelResponse from(List<ScheduledTransferCancelResult> results) {
        return new ScheduledTransferCancelResponse(
                ScheduledTransferCancelSummaryResponse.from(results),
                results.stream().map(ScheduledTransferCancelItemResponse::from).toList());
    }
}
