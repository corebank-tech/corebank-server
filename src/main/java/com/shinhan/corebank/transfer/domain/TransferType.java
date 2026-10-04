package com.shinhan.corebank.transfer.domain;

public enum TransferType {
    // 즉시이체
    IMMEDIATE,

    // 예약이체
    SCHEDULED,

    // 자동이체
    AUTO;

    /** ledger_entry.transaction_type 값. TransferExecutionService.resolveTransactionType과 같은 매핑이다. */
    public String ledgerTransactionType() {
        return switch (this) {
            case IMMEDIATE -> "IMMEDIATE_TRANSFER";
            case SCHEDULED -> "SCHEDULED_TRANSFER";
            case AUTO -> "AUTO_TRANSFER";
        };
    }
}
