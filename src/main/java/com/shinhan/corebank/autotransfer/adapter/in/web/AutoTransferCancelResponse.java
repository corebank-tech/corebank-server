package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferCancelResult;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AutoTransferCancelResponse(
        @Schema(description = "해지 성공·실패 건수 집계")
        AutoTransferCancelSummaryResponse summary,
        @Schema(description = "요청한 ID 순서(오름차순)대로의 건별 처리결과")
        List<AutoTransferCancelItemResponse> items) {

    public static AutoTransferCancelResponse from(List<AutoTransferCancelResult> results) {
        return new AutoTransferCancelResponse(
                AutoTransferCancelSummaryResponse.from(results),
                results.stream().map(AutoTransferCancelItemResponse::from).toList());
    }
}
