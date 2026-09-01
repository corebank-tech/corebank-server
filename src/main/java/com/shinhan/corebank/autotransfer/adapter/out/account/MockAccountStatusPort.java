package com.shinhan.corebank.autotransfer.adapter.out.account;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// 이 포트의 다른 구현체가 없어 @Profile로 좁히면 prod 기동이 실패한다 - 실구현 전까지 모든 프로필에서 활성화한다.
@Component
@RequiredArgsConstructor
@Profile({"local", "test", "scratch"})
public class MockAccountStatusPort implements AccountStatusPort {
    private final EntityManager entityManager;

    @Override
    public boolean isActiveAccount(Long accountId) {
        return true;
    }

    @Override
    public Optional<AccountType> findAccountTypeByNumber(String accountNumber) {
        return Optional.of(AccountType.DEMAND_DEPOSIT);
    }

    @Override
    public boolean belongsToCustomer(Long accountId, Long customerId) {
        Number count = (Number) entityManager
                .createNativeQuery(
                        "SELECT COUNT(*) FROM account WHERE account_id = :accountId AND customer_id = :customerId")
                .setParameter("accountId", accountId)
                .setParameter("customerId", customerId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    public Optional<String> findAccountAlias(Long accountId) {
        List<?> result = entityManager
                .createNativeQuery("SELECT alias FROM account WHERE account_id = :accountId")
                .setParameter("accountId", accountId)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.ofNullable((String) result.get(0));
    }

    @Override
    public boolean isWithdrawalRegistered(Long accountId) {
        List<?> result = entityManager
                .createNativeQuery("SELECT withdrawal_registered FROM account WHERE account_id = :accountId")
                .setParameter("accountId", accountId)
                .getResultList();
        return !result.isEmpty() && Boolean.TRUE.equals(result.get(0));
    }
}
