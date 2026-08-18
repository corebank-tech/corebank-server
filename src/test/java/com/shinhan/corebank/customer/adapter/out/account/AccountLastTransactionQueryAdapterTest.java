package com.shinhan.corebank.customer.adapter.out.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AccountLastTransactionQueryAdapterTest extends IntegrationTestSupport {

    @Autowired
    AccountLastTransactionQueryAdapter adapter;

    @Autowired
    EntityManager entityManager;

    private static final AtomicLong CUSTOMER_SEQ = new AtomicLong();
    private static final AtomicLong ACCOUNT_SEQ = new AtomicLong();

    @Test
    @DisplayName("보유 계좌 중 가장 최근 거래일시를 반환한다")
    void findLatestTransactionAt_returnsMostRecentAmongAccounts() {
        Long customerId = insertCustomer();
        insertAccount(customerId, LocalDateTime.of(2026, 3, 1, 8, 0));
        insertAccount(customerId, LocalDateTime.of(2026, 3, 9, 15, 0)); // 가장 최근
        insertAccount(customerId, LocalDateTime.of(2026, 3, 5, 10, 0));
        entityManager.flush();
        entityManager.clear();

        Optional<LocalDateTime> result = adapter.findLatestTransactionAt(customerId);

        assertThat(result).contains(LocalDateTime.of(2026, 3, 9, 15, 0));
    }

    @Test
    @DisplayName("보유 계좌가 없으면 빈 Optional을 반환한다")
    void findLatestTransactionAt_noAccounts_returnsEmpty() {
        Long customerId = insertCustomer();

        Optional<LocalDateTime> result = adapter.findLatestTransactionAt(customerId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("보유 계좌가 있어도 전부 거래 이력이 없으면 빈 Optional을 반환한다")
    void findLatestTransactionAt_noTransactionHistory_returnsEmpty() {
        Long customerId = insertCustomer();
        insertAccount(customerId, null);

        Optional<LocalDateTime> result = adapter.findLatestTransactionAt(customerId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("다른 고객의 계좌는 섞이지 않는다")
    void findLatestTransactionAt_doesNotMixOtherCustomers() {
        Long customerId = insertCustomer();
        Long otherCustomerId = insertCustomer();
        insertAccount(otherCustomerId, LocalDateTime.of(2026, 3, 20, 9, 0));
        insertAccount(customerId, LocalDateTime.of(2026, 3, 1, 8, 0));

        Optional<LocalDateTime> result = adapter.findLatestTransactionAt(customerId);

        assertThat(result).contains(LocalDateTime.of(2026, 3, 1, 8, 0));
    }

    private Long insertCustomer() {
        long seq = CUSTOMER_SEQ.incrementAndGet();
        String userId = "u" + seq;
        String email = "test" + seq + "@test.com";
        entityManager.createNativeQuery(
                        "INSERT INTO customer (user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at) "
                                + "VALUES (:userId, 'x', '홍길동', '1990-01-01', :email, '01012345678', NOW(), NOW(), NOW())")
                .setParameter("userId", userId)
                .setParameter("email", email)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private void insertAccount(Long customerId, LocalDateTime lastTransactionAt) {
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, "
                                + "opened_date, last_transaction_at, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', NOW(), "
                                + ":lastTransactionAt, NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .setParameter("lastTransactionAt", lastTransactionAt)
                .executeUpdate();
    }
}
