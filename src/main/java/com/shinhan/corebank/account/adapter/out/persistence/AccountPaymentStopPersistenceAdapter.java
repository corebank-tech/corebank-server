package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.application.port.out.AccountPaymentStopPort;
import com.shinhan.corebank.account.domain.AccountPaymentStop;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountPaymentStopPersistenceAdapter implements AccountPaymentStopPort {

    private final AccountPaymentStopJpaRepository repository;

    @Override
    public Optional<AccountPaymentStop> findByAccountIdAndCaseId(long accountId, long caseId) {
        return repository.findByAccountIdAndCaseId(accountId, caseId).map(AccountPaymentStopJpaEntity::toDomain);
    }

    @Override
    public List<AccountPaymentStop> findActiveByAccountId(long accountId) {
        return repository.findByAccountIdAndActiveTrueOrderByPaymentStopIdAsc(accountId).stream()
                .map(AccountPaymentStopJpaEntity::toDomain)
                .toList();
    }

    @Override
    public AccountPaymentStop save(AccountPaymentStop paymentStop) {
        if (paymentStop.getPaymentStopId() == null) {
            return repository
                    .save(AccountPaymentStopJpaEntity.from(paymentStop))
                    .toDomain();
        }

        AccountPaymentStopJpaEntity entity = repository
                .findById(paymentStop.getPaymentStopId())
                .orElseThrow(() ->
                        new IllegalStateException("지급정지 기록이 없습니다. paymentStopId=" + paymentStop.getPaymentStopId()));
        entity.updateFrom(paymentStop);

        return entity.toDomain();
    }

    @Override
    public boolean existsOtherCase(long accountId, long excludingCaseId) {
        return repository.existsByAccountIdAndCaseIdNot(accountId, excludingCaseId);
    }
}
