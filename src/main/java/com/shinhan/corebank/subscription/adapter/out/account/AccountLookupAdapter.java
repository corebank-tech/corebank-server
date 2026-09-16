package com.shinhan.corebank.subscription.adapter.out.account;

import com.shinhan.corebank.account.application.port.in.WithdrawableAccountQueryUseCase;
import com.shinhan.corebank.subscription.application.port.out.AccountLookupPort;
import com.shinhan.corebank.subscription.application.port.out.WithdrawableAccount;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountLookupAdapter implements AccountLookupPort {

    private final WithdrawableAccountQueryUseCase withdrawableAccountQueryUseCase;

    @Override
    public Optional<WithdrawableAccount> findWithdrawable(Long accountId, Long customerId) {
        return withdrawableAccountQueryUseCase
                .findWithdrawable(accountId, customerId)
                .map(result -> new WithdrawableAccount(result.accountId(), result.accountNumber(), result.balance()));
    }
}
