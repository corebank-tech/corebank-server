package com.shinhan.corebank.transfer.application.port.out;

// TransferHistoryQueryPort#summarize의 반환 타입. port/in의 TransferHistorySummary와 필드는
// 같지만, out 포트가 in 포트 DTO를 직접 반환하지 않는다는 경계를 지키기 위해 따로 둔다
// (LedgerHistoryAggregate·ScheduledTransferExecutionResultAggregate와 동일 이유).
public record TransferHistoryAggregate(long successCount, long successAmount, long errorCount, long errorAmount) {
    public static TransferHistoryAggregate empty() {
        return new TransferHistoryAggregate(0L, 0L, 0L, 0L);
    }
}
