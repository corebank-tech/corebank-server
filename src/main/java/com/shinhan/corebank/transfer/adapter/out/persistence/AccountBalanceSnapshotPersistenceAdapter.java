package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.out.AccountBalanceSnapshotPort;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AccountBalanceSnapshotPersistenceAdapter implements AccountBalanceSnapshotPort {

    private final AccountLockJpaRepository repository;

    public AccountBalanceSnapshotPersistenceAdapter(AccountLockJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<Long, Long> findBalancesByAccountIds(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return repository.findBalancesByAccountIds(accountIds).stream()
                .collect(Collectors.toMap(
                        AccountLockJpaRepository.AccountBalanceProjection::getAccountId,
                        AccountLockJpaRepository.AccountBalanceProjection::getBalance));
    }
}
