package com.shinhan.corebank.transfer.application.port.in;

import java.util.List;
import lombok.Builder;

@Builder
public record LedgerHistoryResult(
        LedgerHistorySummary summary,
        int page,
        int size,
        long totalCount,
        int totalPages,
        List<LedgerHistoryItem> items) {}
