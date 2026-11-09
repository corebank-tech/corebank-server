package com.shinhan.corebank.transfer.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface LedgerAccumulatedBalanceQuery {

    Map<Long, Long> calculate(List<Long> accountIds, LocalDate fromInclusive, LocalDate toExclusive);
}
