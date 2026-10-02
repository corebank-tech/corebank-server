package com.shinhan.corebank.subscription.adapter.out.transfer;

import com.shinhan.corebank.subscription.application.port.out.LedgerBalanceHistoryPort;
import com.shinhan.corebank.subscription.application.port.out.LedgerBalanceHistoryResult;
import com.shinhan.corebank.subscription.domain.LedgerBalancePoint;
import com.shinhan.corebank.transfer.api.LedgerBalanceHistory;
import com.shinhan.corebank.transfer.api.LedgerBalanceQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerBalanceHistoryAdapter implements LedgerBalanceHistoryPort {

    private final LedgerBalanceQuery ledgerBalanceQuery;

    @Override
    public LedgerBalanceHistoryResult load(
            Long accountId, java.time.LocalDate fromInclusive, java.time.LocalDate toExclusive) {

        LedgerBalanceHistory history = ledgerBalanceQuery.query(accountId, fromInclusive, toExclusive);

        return new LedgerBalanceHistoryResult(
                history.openingBalance(),
                history.entries().stream()
                        .map(entry -> new LedgerBalancePoint(
                                entry.date(), entry.occurredAt(), entry.ledgerEntryId(), entry.balanceAfter()))
                        .toList());
    }
}
