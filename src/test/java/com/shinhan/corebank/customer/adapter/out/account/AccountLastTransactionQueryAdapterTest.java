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
    private static final AtomicLong LEDGER_SEQ = new AtomicLong();

    @Test
    @DisplayName("보유 계좌 중 가장 최근 거래일시(ledger_entry 기준)를 반환한다")
    void findLatestTransactionAt_returnsMostRecentAmongAccounts() {
        Long customerId = insertCustomer();
        Long accountId1 = insertAccount(customerId);
        Long accountId2 = insertAccount(customerId);
        insertLedgerEntry(accountId1, LocalDateTime.of(2026, 3, 1, 8, 0));
        insertLedgerEntry(accountId2, LocalDateTime.of(2026, 3, 9, 15, 0)); // 가장 최근
        insertLedgerEntry(accountId1, LocalDateTime.of(2026, 3, 5, 10, 0));
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
    @DisplayName("보유 계좌가 있어도 원장에 거래 이력이 없으면 빈 Optional을 반환한다")
    void findLatestTransactionAt_noLedgerEntries_returnsEmpty() {
        Long customerId = insertCustomer();
        insertAccount(customerId);

        Optional<LocalDateTime> result = adapter.findLatestTransactionAt(customerId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("다른 고객의 계좌·원장은 섞이지 않는다")
    void findLatestTransactionAt_doesNotMixOtherCustomers() {
        Long customerId = insertCustomer();
        Long otherCustomerId = insertCustomer();
        Long otherAccountId = insertAccount(otherCustomerId);
        Long accountId = insertAccount(customerId);
        insertLedgerEntry(otherAccountId, LocalDateTime.of(2026, 3, 20, 9, 0));
        insertLedgerEntry(accountId, LocalDateTime.of(2026, 3, 1, 8, 0));

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

    private Long insertAccount(Long customerId) {
        String accountNumber = String.format("%012d", ACCOUNT_SEQ.incrementAndGet());
        entityManager.createNativeQuery(
                        "INSERT INTO account (account_number, customer_id, account_type, status, password_hash, "
                                + "opened_date, created_at, updated_at) "
                                + "VALUES (:accountNumber, :customerId, 'DEMAND_DEPOSIT', 'ACTIVE', 'x', NOW(), NOW(), NOW())")
                .setParameter("accountNumber", accountNumber)
                .setParameter("customerId", customerId)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    }

    private void insertLedgerEntry(Long accountId, LocalDateTime occurredAt) {
        // transaction_number 형식: YYYYMMDD(8) + 채널 영문 2 + 일련 10 = 20자 (ck_le_txno)
        String transactionNumber = "20260301WB" + String.format("%010d", LEDGER_SEQ.incrementAndGet());
        entityManager.createNativeQuery(
                        "INSERT INTO ledger_entry (account_id, transaction_number, direction, amount, balance_after, "
                                + "transaction_type, channel, occurred_at) "
                                + "VALUES (:accountId, :transactionNumber, 'WITHDRAWAL', 10000, 100000, "
                                + "'IMMEDIATE_TRANSFER', 'WB', :occurredAt)")
                .setParameter("accountId", accountId)
                .setParameter("transactionNumber", transactionNumber)
                .setParameter("occurredAt", occurredAt)
                .executeUpdate();
    }
}
