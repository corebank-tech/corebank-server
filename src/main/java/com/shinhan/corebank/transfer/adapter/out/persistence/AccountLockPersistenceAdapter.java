package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccount;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountsForTransfer;

import org.springframework.stereotype.Component;

@Component
public class AccountLockPersistenceAdapter implements AccountLockPort {

    private final AccountLockJpaRepository repository;

    public AccountLockPersistenceAdapter(AccountLockJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public LockedAccountsForTransfer lockForTransfer(Long withdrawalAccountId, Long depositAccountId) {
        Long firstId = Math.min(withdrawalAccountId, depositAccountId);
        Long secondId = Math.max(withdrawalAccountId, depositAccountId);

        AccountLockJpaEntity first = findForUpdate(firstId);
        AccountLockJpaEntity second = findForUpdate(secondId);

        AccountLockJpaEntity withdrawalEntity =
                withdrawalAccountId.equals(firstId) ? first : second;
        AccountLockJpaEntity depositEntity =
                depositAccountId.equals(firstId) ? first : second;

        return new LockedAccountsForTransfer(toLockedAccount(withdrawalEntity), toLockedAccount(depositEntity));
    }

    private AccountLockJpaEntity findForUpdate(Long accountId) {
        return repository.findByAccountIdForUpdate(accountId).orElseThrow();
    }

    private LockedAccount toLockedAccount(AccountLockJpaEntity entity) {
        return new LockedAccount(entity.getAccountId(), entity.getBalance(), entity.getStatus());
    }
}
