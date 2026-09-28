package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.out.LedgerReconciliationPort;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LedgerReconciliationPersistenceAdapter implements LedgerReconciliationPort {

    private final LedgerEntryJpaRepository repository;

    public LedgerReconciliationPersistenceAdapter(LedgerEntryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Long> findAccountIdsPostedBetween(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return repository.findDistinctAccountIdsPostedBetween(fromInclusive, toExclusive);
    }

    @Override
    public Map<Long, Long> sumSignedAmountByAccountIds(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return repository.sumSignedAmountByAccountIds(accountIds).stream()
                .collect(Collectors.toMap(
                        LedgerEntryJpaRepository.AccountLedgerSumProjection::getAccountId,
                        LedgerEntryJpaRepository.AccountLedgerSumProjection::getSignedSum));
    }
}
