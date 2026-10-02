package com.shinhan.corebank.transfer.api;

import java.util.List;

public record LedgerBalanceHistory(long openingBalance, List<LedgerBalanceEntry> entries) {

    public LedgerBalanceHistory {
        entries = List.copyOf(entries);
    }
}
