package com.shinhan.corebank.autotransfer.adapter.out.account;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Profile({"local", "test", "scratch"})
public class MockAccountStatusPort implements AccountStatusPort {
    @Override
    public boolean isActiveAccount(Long accountId) {
        return true;
    }
    @Override
    public Optional<AccountType> findAccountTypeByNumber(String accountNumber) {
        return Optional.of(AccountType.DEMAND_DEPOSIT);
    }
}