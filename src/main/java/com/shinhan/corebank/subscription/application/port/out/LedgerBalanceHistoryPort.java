package com.shinhan.corebank.subscription.application.port.out;

import java.time.LocalDate;

public interface LedgerBalanceHistoryPort {

    LedgerBalanceHistory load(Long accountId, LocalDate fromInclusive, LocalDate toExclusive);
}
