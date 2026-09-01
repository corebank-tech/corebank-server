package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.AccountNumberQueryUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountNumberQueryService implements AccountNumberQueryUseCase {

    private final AccountPersistencePort accountPersistencePort;

    @Override
    public Optional<String> findAccountNumber(Long accountId, Long customerId) {
        return accountPersistencePort
                .findByAccountIdAndCustomerId(accountId, customerId)
                .map(Account::getAccountNumber);
    }
}
