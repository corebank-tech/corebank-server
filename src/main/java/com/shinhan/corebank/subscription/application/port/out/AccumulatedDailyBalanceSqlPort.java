package com.shinhan.corebank.subscription.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AccumulatedDailyBalanceSqlPort {

    Map<Long, Long> calculate(List<Long> accountIds, LocalDate fromInclusive, LocalDate toExclusive);
}
