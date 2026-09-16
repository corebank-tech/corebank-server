package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.WithdrawableAccountQueryUseCase;
import com.shinhan.corebank.account.application.port.in.WithdrawableAccountResult;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.AccountStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WithdrawableAccountQueryService implements WithdrawableAccountQueryUseCase {

    private final AccountPersistencePort accountPersistencePort;

    @Override
    public Optional<WithdrawableAccountResult> findWithdrawable(Long accountId, Long customerId) {
        return accountPersistencePort
                .findByAccountIdAndCustomerId(accountId, customerId)
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .filter(Account::isWithdrawalRegistered)
                .map(account -> new WithdrawableAccountResult(
                        account.getAccountId(), account.getAccountNumber(), account.getBalance()));
    }
}
