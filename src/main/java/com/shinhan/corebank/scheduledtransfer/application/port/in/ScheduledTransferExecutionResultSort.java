package com.shinhan.corebank.scheduledtransfer.application.port.in;

// LATEST = 처리결과 기준일(executedAt/canceledAt) 내림차순, OLDEST = 오름차순 (LedgerHistorySort와 동일 패턴)
public enum ScheduledTransferExecutionResultSort {
    LATEST,
    OLDEST
}
