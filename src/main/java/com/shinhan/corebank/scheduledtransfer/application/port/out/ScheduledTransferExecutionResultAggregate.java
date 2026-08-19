package com.shinhan.corebank.scheduledtransfer.application.port.out;

public record ScheduledTransferExecutionResultAggregate(long successCount,
                                                         long successAmount,
                                                         long failedCount,
                                                         long failedAmount,
                                                         long canceledCount,
                                                         long canceledAmount) {
    public static ScheduledTransferExecutionResultAggregate empty() {
        return new ScheduledTransferExecutionResultAggregate(0L, 0L, 0L, 0L, 0L, 0L);
    }
}
