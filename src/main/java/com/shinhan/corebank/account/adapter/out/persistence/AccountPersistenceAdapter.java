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
        AccountJpaEntity entity = AccountMapper.toEntity(account);
        AccountJpaEntity savedEntity = accountJpaRepository.save(entity);

        return AccountMapper.toDomain(savedEntity);
    }
}