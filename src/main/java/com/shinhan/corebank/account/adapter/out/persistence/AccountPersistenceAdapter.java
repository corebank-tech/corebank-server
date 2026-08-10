package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountPersistenceAdapter implements AccountPersistencePort {

    private final AccountJpaRepository accountJpaRepository;

    @Override
    public Account save(Account account) {
        if (account.getAccountId() == null) {
                return saveNewAccount(account);
            }

        return updateExistingAccount(account);
    }

    private Account saveNewAccount(Account account) {
        AccountJpaEntity entity =
                AccountMapper.toEntity(account);

        AccountJpaEntity savedEntity =
                accountJpaRepository.save(entity);

        return AccountMapper.toDomain(savedEntity);
    }

    private Account updateExistingAccount(Account account) {
        AccountJpaEntity entity = accountJpaRepository
                .findById(account.getAccountId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "저장할 계좌를 찾을 수 없습니다."
                        )
                );

        entity.updateFrom(account);

        AccountJpaEntity savedEntity =
                accountJpaRepository.save(entity);

        return AccountMapper.toDomain(savedEntity);
    }
}