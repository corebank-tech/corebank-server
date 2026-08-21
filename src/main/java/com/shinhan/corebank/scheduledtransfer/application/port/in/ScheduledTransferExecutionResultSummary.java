package com.shinhan.corebank.scheduledtransfer.application.port.in;

public record ScheduledTransferExecutionResultSummary(long successCount,
                                                       long successAmount,
                                                       long failedCount,
                                                       long failedAmount,
                                                       long canceledCount,
                                                       long canceledAmount) {
}
