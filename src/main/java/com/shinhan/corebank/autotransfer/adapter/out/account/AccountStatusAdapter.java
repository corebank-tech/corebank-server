package com.shinhan.corebank.autotransfer.adapter.out.account;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Primary
@RequiredArgsConstructor
public class AccountStatusAdapter implements AccountStatusPort {
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AccountLookupJpaRepository accountLookupJpaRepository;

    @Override
    public boolean isActiveAccount(Long accountId) {
        return accountLookupJpaRepository.findById(accountId)
                .map(entity -> STATUS_ACTIVE.equals(entity.getStatus()))
                .orElse(false);
    }

    @Override
    public Optional<AccountType> findAccountTypeByNumber(String accountNumber) {
        return accountLookupJpaRepository.findByAccountNumber(accountNumber)
                .map(entity -> AccountType.valueOf(entity.getAccountType()));
    }

    @Override
    public boolean belongsToCustomer(Long accountId, Long customerId) {
        return accountLookupJpaRepository.existsByAccountIdAndCustomerId(accountId, customerId);
    }
}
