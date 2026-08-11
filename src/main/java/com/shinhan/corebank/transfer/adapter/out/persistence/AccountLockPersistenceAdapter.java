package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccount;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountsForTransfer;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

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

    @Override
    public void debit(Long accountId, long amount) {
        findForUpdate(accountId).debit(amount);
    }

    @Override
    public void credit(Long accountId, long amount) {
        findForUpdate(accountId).credit(amount);
    }

    private AccountLockJpaEntity findForUpdate(Long accountId) {
        return repository.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.ACCOUNT_LOCK_TARGET_NOT_FOUND));
    }

    private LockedAccount toLockedAccount(AccountLockJpaEntity entity) {
        return new LockedAccount(entity.getAccountId(), entity.getBalance(), entity.getStatus());
    }
}
