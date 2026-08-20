package com.shinhan.corebank.transfer.application.port.in;

import org.springframework.data.domain.Page;

public record TransferHistoryPage(Page<TransferHistoryItem> page, TransferHistorySummary summary) {
}
