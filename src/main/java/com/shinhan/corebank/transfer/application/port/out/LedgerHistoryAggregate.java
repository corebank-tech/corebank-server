package com.shinhan.corebank.transfer.application.port.out;

// LedgerHistoryQueryPort#summarize의 반환 타입. port/in의 LedgerHistorySummary와 필드는
// 같지만, out 포트가 in 포트 DTO를 직접 반환하지 않는다는 경계를 지키기 위해 따로 둔다
// (TransferBalances가 같은 이유로 out 포트 전용 DTO인 것과 동일).
public record LedgerHistoryAggregate(
        long depositCount, long depositAmount, long withdrawalCount, long withdrawalAmount) {
    public static LedgerHistoryAggregate empty() {
        return new LedgerHistoryAggregate(0L, 0L, 0L, 0L);
    }
}
