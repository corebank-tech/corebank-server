package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryItem;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryQuery;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistoryResult;
import com.shinhan.corebank.transfer.application.port.in.LedgerHistorySummary;
import com.shinhan.corebank.transfer.application.port.out.LedgerHistoryAggregate;
import com.shinhan.corebank.transfer.application.port.out.LedgerHistoryQueryPort;
import com.shinhan.corebank.transfer.domain.LedgerEntry;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LedgerHistoryQueryService implements LedgerHistoryQueryUseCase {

    private final LedgerHistoryQueryPort ledgerHistoryQueryPort;

    public LedgerHistoryQueryService(LedgerHistoryQueryPort ledgerHistoryQueryPort) {
        this.ledgerHistoryQueryPort = ledgerHistoryQueryPort;
    }

    @Override
    public LedgerHistoryResult query(LedgerHistoryQuery query) {
        List<LedgerEntry> entries = ledgerHistoryQueryPort.findEntries(query);
        long totalCount = ledgerHistoryQueryPort.countEntries(query);
        LedgerHistoryAggregate aggregate = ledgerHistoryQueryPort.summarize(query);

        return LedgerHistoryResult.builder()
                .summary(toSummary(aggregate))
                .page(query.all() ? 0 : query.page())
                .size(query.all() ? (int) totalCount : query.size())
                .totalCount(totalCount)
                .totalPages(query.all() ? 1 : totalPages(totalCount, query.size()))
                .items(entries.stream().map(this::toItem).toList())
                .build();
    }

    private int totalPages(long totalCount, int size) {
        if (totalCount == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalCount / size);
    }

    private LedgerHistorySummary toSummary(LedgerHistoryAggregate aggregate) {
        return LedgerHistorySummary.builder()
                .depositCount(aggregate.depositCount())
                .depositAmount(aggregate.depositAmount())
                .withdrawalCount(aggregate.withdrawalCount())
                .withdrawalAmount(aggregate.withdrawalAmount())
                .build();
    }

    private LedgerHistoryItem toItem(LedgerEntry entry) {
        return LedgerHistoryItem.builder()
                .ledgerEntryId(entry.getLedgerEntryId())
                .transactionNumber(entry.getTransactionNumber())
                .occurredAt(entry.getOccurredAt())
                .transactionType(entry.getTransactionType())
                .direction(entry.getDirection())
                .amount(entry.getAmount())
                .transactionContent(entry.getTransactionContent())
                .balanceAfter(entry.getBalanceAfter())
                .channel(entry.getChannel())
                .reversed(entry.isReversed())
                .reversalId(entry.getReversalId())
                .build();
    }
}
