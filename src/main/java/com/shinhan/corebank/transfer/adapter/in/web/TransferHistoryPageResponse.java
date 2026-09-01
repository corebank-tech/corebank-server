package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.TransferHistoryItem;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryPage;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

public record TransferHistoryPageResponse(
        OffsetDateTime asOf,
        TransferHistorySummaryResponse summary,
        int page,
        int size,
        long totalCount,
        int totalPages,
        List<TransferHistoryItemResponse> items) {
    public static TransferHistoryPageResponse from(TransferHistoryPage result) {
        Page<TransferHistoryItem> page = result.page();
        return new TransferHistoryPageResponse(
                result.asOf(),
                TransferHistorySummaryResponse.from(result.summary()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent().stream()
                        .map(TransferHistoryItemResponse::from)
                        .toList());
    }
}
