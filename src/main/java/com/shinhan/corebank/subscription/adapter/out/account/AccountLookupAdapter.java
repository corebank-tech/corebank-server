package com.shinhan.corebank.subscription.adapter.out.account;

import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.subscription.application.port.out.AccountLookupPort;
import com.shinhan.corebank.subscription.application.port.out.WithdrawableAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AccountLookupAdapter implements AccountLookupPort {

    private final SubscriptionAccountJpaRepository repository;

    @Override
    public Optional<WithdrawableAccount> findWithdrawable(Long accountId, Long customerId) {
        return repository.findById(accountId)
                .filter(entity -> entity.getCustomerId().equals(customerId))
                .filter(entity -> entity.getStatus() == AccountStatus.ACTIVE)
                .filter(SubscriptionAccountJpaEntity::isWithdrawalRegistered)
                .map(entity -> new WithdrawableAccount(
                        entity.getAccountId(), entity.getAccountNumber(), entity.getBalance()));
    }
}
