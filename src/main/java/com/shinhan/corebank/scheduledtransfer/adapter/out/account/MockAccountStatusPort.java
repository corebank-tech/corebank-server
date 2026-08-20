package com.shinhan.corebank.scheduledtransfer.adapter.out.account;

import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.scheduledtransfer.application.port.out.AccountStatusPort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

// 이 포트의 다른 구현체가 없어 @Profile로 좁히면 prod 기동이 실패한다 - 실구현 전까지 모든 프로필에서 활성화한다.
// 빈 이름 명시: autotransfer.adapter.out.account.MockAccountStatusPort와 클래스 단순이름이 같아 기본 빈 이름(mockAccountStatusPort)이 충돌한다.
@Component("scheduledTransferMockAccountStatusPort")
@RequiredArgsConstructor
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
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM account WHERE account_id = :accountId AND customer_id = :customerId")
                .setParameter("accountId", accountId)
                .setParameter("customerId", customerId)
                .getSingleResult();
        return count.longValue() > 0;
    }

    @Override
    public Optional<String> findAccountNumberById(Long accountId) {
        List<?> result = entityManager.createNativeQuery(
                        "SELECT account_number FROM account WHERE account_id = :accountId")
                .setParameter("accountId", accountId)
                .getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of((String) result.get(0));
    }

    @Override
    public Map<Long, String> findAccountNumbersByIds(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT account_id, account_number FROM account WHERE account_id IN (:accountIds)")
                .setParameter("accountIds", accountIds)
                .getResultList();
        Map<Long, String> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).longValue(), (String) row[1]);
        }
        return result;
    }

    @Override
    public Map<Long, String> findAccountAliasesByIds(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        // alias는 nullable - null인 행은 결과 Map에서 제외(별칭 미설정)
        List<Object[]> rows = entityManager.createNativeQuery(
                        "SELECT account_id, alias FROM account WHERE account_id IN (:accountIds) AND alias IS NOT NULL")
                .setParameter("accountIds", accountIds)
                .getResultList();
        Map<Long, String> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put(((Number) row[0]).longValue(), (String) row[1]);
        }
        return result;
    }
}
