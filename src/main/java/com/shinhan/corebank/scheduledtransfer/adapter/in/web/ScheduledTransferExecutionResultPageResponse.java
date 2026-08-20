package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultItem;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultPage;
import org.springframework.data.domain.Page;

import java.util.List;

public record ScheduledTransferExecutionResultPageResponse(ScheduledTransferExecutionResultSummaryResponse summary,
                                                            int page,
                                                            int size,
                                                            long totalCount,
                                                            int totalPages,
                                                            List<ScheduledTransferExecutionResultItemResponse> items) {
    public static ScheduledTransferExecutionResultPageResponse from(ScheduledTransferExecutionResultPage result) {
        Page<ScheduledTransferExecutionResultItem> page = result.page();
        return new ScheduledTransferExecutionResultPageResponse(
                ScheduledTransferExecutionResultSummaryResponse.from(result.summary()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getContent().stream().map(ScheduledTransferExecutionResultItemResponse::from).toList());
    }
}
