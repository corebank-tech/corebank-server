package com.shinhan.corebank.transfer.application.port.in;

import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;

public record TransferHistoryPage(
        OffsetDateTime asOf, Page<TransferHistoryItem> page, TransferHistorySummary summary) {}
