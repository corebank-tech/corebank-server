package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.api.LedgerAccumulatedBalanceQuery;
import com.shinhan.corebank.transfer.application.port.out.LedgerAccumulatedBalancePort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LedgerAccumulatedBalanceQueryService implements LedgerAccumulatedBalanceQuery {

    private final LedgerAccumulatedBalancePort ledgerAccumulatedBalancePort;

    @Override
    public Map<Long, Long> calculate(List<Long> accountIds, LocalDate fromInclusive, LocalDate toExclusive) {

        return ledgerAccumulatedBalancePort.calculate(accountIds, fromInclusive, toExclusive);
    }
}
