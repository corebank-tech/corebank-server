package com.shinhan.corebank.subscription.application.port.in;

import java.time.LocalDate;

public interface AccumulatedDailyBalanceUseCase {

    long calculate(Long accountId, LocalDate fromInclusive, LocalDate toExclusive);
}
