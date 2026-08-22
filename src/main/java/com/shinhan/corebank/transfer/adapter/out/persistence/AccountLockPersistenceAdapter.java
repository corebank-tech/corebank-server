package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccount;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountType;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountsForTransfer;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import com.shinhan.corebank.transfer.application.port.out.TransferBalances;
import com.shinhan.corebank.transfer.application.port.out.WithdrawalAccountDetail;
import com.shinhan.corebank.transfer.application.port.out.WithdrawalAccountPreCheckSnapshot;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import org.springframework.stereotype.Component;

@Component
public class AccountLockPersistenceAdapter implements AccountLockPort {

    private final AccountLockJpaRepository repository;

    public AccountLockPersistenceAdapter(AccountLockJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ResolvedPayee> resolvePayeeByAccountNumber(String accountNumber) {
        return repository.findPayeeByAccountNumber(accountNumber)
                .map(row -> new ResolvedPayee(
                        row.getAccountId(),
                        row.getPayeeName(),
                        LockedAccountType.valueOf(row.getAccountType()),
                        LockedAccountStatus.valueOf(row.getStatus())));
    }

    @Override
    public Map<String, ResolvedPayee> resolvePayeesByAccountNumbers(Collection<String> accountNumbers) {
        if (accountNumbers.isEmpty()) {
            return Map.of();
        }
        return repository.findPayeesByAccountNumbers(accountNumbers).stream()
                .collect(Collectors.toMap(
                        AccountLockJpaRepository.BatchPayeeProjection::getAccountNumber,
                        row -> new ResolvedPayee(
                                row.getAccountId(),
                                row.getPayeeName(),
                                LockedAccountType.valueOf(row.getAccountType()),
                                LockedAccountStatus.valueOf(row.getStatus()))));
    }

    @Override
    public Optional<WithdrawalAccountDetail> findWithdrawalAccountDetail(Long accountId) {
        return repository.findWithdrawalAccountDetailByAccountId(accountId);
    }

    @Override
    public Optional<WithdrawalAccountPreCheckSnapshot> findWithdrawalAccountPreCheckSnapshot(Long accountId) {
        return repository.findWithdrawalAccountPreCheckByAccountId(accountId)
                .map(row -> new WithdrawalAccountPreCheckSnapshot(
                        LockedAccountStatus.valueOf(row.getStatus()), row.getBalance()));
    }

    @Override
    public LockedAccountsForTransfer lockForTransfer(Long withdrawalAccountId, Long depositAccountId) {
        if (withdrawalAccountId == null || depositAccountId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (withdrawalAccountId.equals(depositAccountId)) {
            throw new BusinessException(TransferErrorCode.SAME_ACCOUNT_TRANSFER);
        }

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
    public TransferBalances applyTransfer(LockedAccountsForTransfer locked, long amount, LocalDateTime executedAt) {
        Long withdrawalAccountId = locked.withdrawal().accountId();
        Long depositAccountId = locked.deposit().accountId();

        // lockForTransfer와 동일하게 오름차순으로 재조회한다. 같은 트랜잭션이 이미 보유한
        // 락을 다시 얻는 것이라 데드락 위험은 없지만, 호출 순서 규칙을 한 곳에서만 정의해 둔다.
        Long firstId = Math.min(withdrawalAccountId, depositAccountId);
        Long secondId = Math.max(withdrawalAccountId, depositAccountId);

        AccountLockJpaEntity first = findForUpdate(firstId);
        AccountLockJpaEntity second = findForUpdate(secondId);

        AccountLockJpaEntity withdrawalEntity =
                withdrawalAccountId.equals(firstId) ? first : second;
        AccountLockJpaEntity depositEntity =
                depositAccountId.equals(firstId) ? first : second;

        withdrawalEntity.debit(amount, executedAt);
        depositEntity.credit(amount, executedAt);

        return new TransferBalances(withdrawalEntity.getBalance(), depositEntity.getBalance());
    }

    private AccountLockJpaEntity findForUpdate(Long accountId) {
        return repository.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new BusinessException(TransferErrorCode.ACCOUNT_LOCK_TARGET_NOT_FOUND));
    }

    private LockedAccount toLockedAccount(AccountLockJpaEntity entity) {
        return new LockedAccount(
                entity.getAccountId(),
                entity.getAccountNumber(),
                entity.getBalance(),
                LockedAccountStatus.valueOf(entity.getStatus())
        );
    }
}
