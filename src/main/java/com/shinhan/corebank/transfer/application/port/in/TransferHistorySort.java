package com.shinhan.corebank.transfer.application.port.in;

// LATEST = transferredAt 내림차순, OLDEST = 오름차순 (LedgerHistorySort·ScheduledTransferExecutionResultSort와 동일 패턴)
public enum TransferHistorySort {
    LATEST,
    OLDEST
}
