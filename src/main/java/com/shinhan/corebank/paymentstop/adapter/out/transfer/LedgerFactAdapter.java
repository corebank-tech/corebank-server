package com.shinhan.corebank.paymentstop.adapter.out.transfer;

import com.shinhan.corebank.paymentstop.application.port.out.LedgerFactPort;
import com.shinhan.corebank.paymentstop.domain.gate.LedgerFact;
import com.shinhan.corebank.transfer.api.CounterpartyTransfer;
import com.shinhan.corebank.transfer.api.CounterpartyTransferQuery;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LedgerFactAdapter implements LedgerFactPort {

    private final CounterpartyTransferQuery counterpartyTransferQuery;

    @Override
    public List<LedgerFact> findByAccount(long accountId, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return counterpartyTransferQuery.findByAccount(accountId, fromInclusive, toExclusive).stream()
                .map(LedgerFactAdapter::toFact)
                .toList();
    }

    private static LedgerFact toFact(CounterpartyTransfer transfer) {
        return new LedgerFact(
                transfer.transactionNumber(),
                transfer.direction() == CounterpartyTransfer.Direction.IN,
                transfer.counterpartyKind() == CounterpartyTransfer.CounterpartyKind.INTERBANK,
                transfer.counterpartyAccountId(),
                transfer.amount(),
                transfer.occurredAt());
    }
}
