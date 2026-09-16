package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountPersistenceAdapter implements AccountPersistencePort {

    private final AccountJpaRepository accountJpaRepository;

    @Override
    public Account save(Account account) {
        if (account.getAccountId() == null) {
            return saveNewAccount(account);
        }

        return updateExistingAccount(account);
    }

    @Override
    public boolean existsByAccountNumber(String accountNumber) {
        return accountJpaRepository.existsByAccountNumber(accountNumber);
    }

    @Override
    public List<Account> findAllByCustomerId(Long customerId) {
        return accountJpaRepository.findAllByCustomerId(customerId).stream()
                .map(AccountMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Account> findByAccountIdAndCustomerId(Long accountId, Long customerId) {
        return accountJpaRepository
                .findByAccountIdAndCustomerId(accountId, customerId)
                .map(AccountMapper::toDomain);
    }

    // 계좌비밀번호 상태 변경 중 동일 계좌 요청을 비관적으로 잠근다.
    @Override
    public Optional<Account> findByAccountIdAndCustomerIdForUpdate(Long accountId, Long customerId) {
        return accountJpaRepository
                .findByAccountIdAndCustomerIdForUpdate(accountId, customerId)
                .map(AccountMapper::toDomain);
    }

    // 계좌비밀번호 관련 상태를 즉시 저장하고 DB 제약을 확인한다.
    @Override
    public Account updatePasswordState(Account account) {
        Objects.requireNonNull(account, "account must not be null");

        AccountJpaEntity entity = accountJpaRepository
                .findById(account.getAccountId())
                .orElseThrow(() -> new IllegalStateException("저장할 계좌를 찾을 수 없습니다."));

        validateVersion(account, entity);
        entity.updatePasswordState(account);

        return AccountMapper.toDomain(accountJpaRepository.saveAndFlush(entity));
    }

    private Account saveNewAccount(Account account) {
        AccountJpaEntity entity = AccountMapper.toEntity(account);

        AccountJpaEntity savedEntity = accountJpaRepository.save(entity);

        return AccountMapper.toDomain(savedEntity);
    }

    private Account updateExistingAccount(Account account) {
        AccountJpaEntity entity = accountJpaRepository
                .findById(account.getAccountId())
                .orElseThrow(() -> new IllegalStateException("저장할 계좌를 찾을 수 없습니다."));

        validateVersion(account, entity);

        entity.updateFrom(account);

        AccountJpaEntity savedEntity = accountJpaRepository.save(entity);

        return AccountMapper.toDomain(savedEntity);
    }

    private void validateVersion(Account account, AccountJpaEntity entity) {
        if (!Objects.equals(account.getVersion(), entity.getVersion())) {
            throw new BusinessException(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
    }
}
