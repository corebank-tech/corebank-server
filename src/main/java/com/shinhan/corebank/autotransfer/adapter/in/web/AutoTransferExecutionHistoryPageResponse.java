package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistoryItem;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistoryResult;

import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.data.domain.Page;

import java.util.List;

// 목록+집계를 합쳐 실제 HTTP data로 나가는 최종 응답
public record AutoTransferExecutionHistoryPageResponse(
        @Schema(description = "조회 기간 내 성공/실패 건수·금액 집계")
        AutoTransferExecutionHistorySummaryResponse summary,
        @Schema(description = "현재 페이지 번호(0부터 시작)")
        int page,
        @Schema(description = "페이지 크기")
        int size,
        @Schema(description = "전체 건수")
        long totalCount,
        @Schema(description = "전체 페이지 수")
        int totalPages,
        @Schema(description = "실행 이력 목록")
        List<AutoTransferExecutionHistoryItemResponse> items) {
    public static AutoTransferExecutionHistoryPageResponse from(AutoTransferExecutionHistoryResult result) {
        Page<AutoTransferExecutionHistoryItem> page = result.page();
        return new AutoTransferExecutionHistoryPageResponse(
                AutoTransferExecutionHistorySummaryResponse.from(result.summary()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent().stream().map(AutoTransferExecutionHistoryItemResponse::from).toList());
    }
}
