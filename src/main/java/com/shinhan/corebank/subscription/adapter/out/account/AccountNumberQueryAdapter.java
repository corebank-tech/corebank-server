package com.shinhan.corebank.subscription.adapter.out.account;

import com.shinhan.corebank.account.application.port.in.AccountNumberQueryUseCase;
import com.shinhan.corebank.subscription.application.port.out.AccountNumberQueryPort;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountNumberQueryAdapter implements AccountNumberQueryPort {

    private final AccountNumberQueryUseCase accountNumberQueryUseCase;

    @Override
    public Optional<String> findAccountNumber(Long accountId, Long customerId) {
        return accountNumberQueryUseCase.findAccountNumber(accountId, customerId);
    }
}
