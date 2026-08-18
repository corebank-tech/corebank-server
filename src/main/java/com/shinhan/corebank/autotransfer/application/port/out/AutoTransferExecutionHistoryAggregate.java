package com.shinhan.corebank.autotransfer.application.port.out;

public record AutoTransferExecutionHistoryAggregate(long successCount,
                                                    long successAmount,
                                                    long errorCount,
                                                    long errorAmount) {
    public static AutoTransferExecutionHistoryAggregate empty() {
        return new AutoTransferExecutionHistoryAggregate(0L, 0L, 0L, 0L);
    }
}
