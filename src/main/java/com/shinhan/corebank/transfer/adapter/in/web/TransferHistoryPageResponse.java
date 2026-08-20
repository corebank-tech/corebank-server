package com.shinhan.corebank.transfer.adapter.in.web;

import java.util.List;

import com.shinhan.corebank.transfer.application.port.in.TransferHistoryItem;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryPage;

import org.springframework.data.domain.Page;

public record TransferHistoryPageResponse(
        TransferHistorySummaryResponse summary,
        int page,
        int size,
        long totalCount,
        int totalPages,
        List<TransferHistoryItemResponse> items
) {
    public static TransferHistoryPageResponse from(TransferHistoryPage result) {
        Page<TransferHistoryItem> page = result.page();
        return new TransferHistoryPageResponse(
                TransferHistorySummaryResponse.from(result.summary()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent().stream().map(TransferHistoryItemResponse::from).toList()
        );
    }
}
