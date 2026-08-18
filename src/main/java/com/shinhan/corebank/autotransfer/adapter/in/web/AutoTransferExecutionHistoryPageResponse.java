package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistoryItem;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferExecutionHistoryResult;
import org.springframework.data.domain.Page;

import java.util.List;

// 목록+집계를 합쳐 실제 HTTP data로 나가는 최종 응답
public record AutoTransferExecutionHistoryPageResponse(AutoTransferExecutionHistorySummaryResponse summary,
                                                       int page,
                                                       int size,
                                                       long totalCount,
                                                       int totalPages,
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
