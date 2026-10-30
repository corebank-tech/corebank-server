package com.shinhan.corebank.transfer.api;

import java.time.LocalDate;

public interface LedgerBalanceQuery {

    LedgerBalanceHistory query(Long accountId, LocalDate fromInclusive, LocalDate toExclusive);
}
