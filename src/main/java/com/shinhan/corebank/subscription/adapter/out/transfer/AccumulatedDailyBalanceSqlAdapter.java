package com.shinhan.corebank.subscription.adapter.out.transfer;

import com.shinhan.corebank.subscription.application.port.out.AccumulatedDailyBalanceSqlPort;
import com.shinhan.corebank.transfer.api.LedgerAccumulatedBalanceQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccumulatedDailyBalanceSqlAdapter implements AccumulatedDailyBalanceSqlPort {

    private final LedgerAccumulatedBalanceQuery ledgerAccumulatedBalanceQuery;

    @Override
    public Map<Long, Long> calculate(List<Long> accountIds, LocalDate fromInclusive, LocalDate toExclusive) {

        return ledgerAccumulatedBalanceQuery.calculate(accountIds, fromInclusive, toExclusive);
    }
}
