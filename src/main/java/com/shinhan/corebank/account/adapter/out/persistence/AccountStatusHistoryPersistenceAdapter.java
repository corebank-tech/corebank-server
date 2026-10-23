package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.application.port.out.AccountStatusHistoryPort;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountStatusHistoryPersistenceAdapter implements AccountStatusHistoryPort {

    private final AccountStatusHistoryJpaRepository repository;

    @Override
    public void recordMaturity(
            Long accountId, LocalDate maturityDate, LocalDate maturityBaseDate, LocalDateTime changedAt) {

        repository.save(AccountStatusHistoryJpaEntity.matured(accountId, maturityDate, maturityBaseDate, changedAt));
    }

    @Override
    public void recordMaturityClosure(
            Long accountId, LocalDate maturityDate, LocalDate closedTradeDate, LocalDateTime changedAt) {

        repository.save(
                AccountStatusHistoryJpaEntity.maturityClosed(accountId, maturityDate, closedTradeDate, changedAt));
    }
}
