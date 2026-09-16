package com.shinhan.corebank.scheduledtransfer.application.port.in;

import org.springframework.data.domain.Page;

public record ScheduledTransferExecutionResultPage(
        Page<ScheduledTransferExecutionResultItem> page, ScheduledTransferExecutionResultSummary summary) {}
