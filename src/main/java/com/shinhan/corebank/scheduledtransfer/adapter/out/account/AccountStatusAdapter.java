package com.shinhan.corebank.scheduledtransfer.adapter.out.account;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AccountStatusPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// 빈 이름 명시: autotransfer.adapter.out.account.AccountStatusAdapter와 클래스 단순이름이 같아
// 기본 빈 이름(accountStatusAdapter)이 충돌한다.
@Component("scheduledTransferAccountStatusAdapter")
@RequiredArgsConstructor
@Profile("prod")
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

    @Override
    public boolean isWithdrawalRegistered(Long accountId) {
        return accountLookupJpaRepository.findById(accountId)
                .map(AccountLookupJpaEntity::isWithdrawalRegistered)
                .orElse(false);
    }

    @Override
    public Optional<String> findAccountNumberById(Long accountId) {
        return accountLookupJpaRepository.findById(accountId)
                .map(AccountLookupJpaEntity::getAccountNumber);
    }

    @Override
    public Map<Long, String> findAccountNumbersByIds(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accountLookupJpaRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(AccountLookupJpaEntity::getAccountId, AccountLookupJpaEntity::getAccountNumber));
    }

    @Override
    public Map<Long, String> findAccountAliasesByIds(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        // alias는 nullable - null인 행은 결과 Map에서 제외(별칭 미설정)
        Map<Long, String> result = new HashMap<>();
        for (AccountLookupJpaEntity entity : accountLookupJpaRepository.findAllById(accountIds)) {
            if (entity.getAlias() != null) {
                result.put(entity.getAccountId(), entity.getAlias());
            }
        }
        return result;
    }
}
