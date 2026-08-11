package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.IntegrationTestSupport;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-TRSF-015 인수기준 재현: A→B, B→A 동시 이체가 데드락 없이 둘 다 완료돼야 한다.
 * 스레드별로 독립된 트랜잭션/커넥션이 필요하므로 이 테스트 클래스는 @Transactional을 두지 않는다.
 */
@DisplayName("계좌 락 오름차순 획득 데드락 회피 동시성 테스트")
class AccountLockDeadlockAvoidanceTest extends IntegrationTestSupport {

    @Autowired
    private AccountLockTransferSimulator simulator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("A→B, B→A 이체를 동시 실행해도 데드락 없이 두 건 모두 처리된다")
    void concurrentCrossTransfers_doNotDeadlock() throws Exception {
        // given
        seedAccounts();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<?> aToB = executor.submit(() -> {
            await(startLatch);
            simulator.execute(301L, 302L, 10_000L);
        });
        Future<?> bToA = executor.submit(() -> {
            await(startLatch);
            simulator.execute(302L, 301L, 5_000L);
        });

        // when
        startLatch.countDown();
        aToB.get(10, TimeUnit.SECONDS);
        bToA.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(balanceOf(301L)).isEqualTo(95_000L);
        assertThat(balanceOf(302L)).isEqualTo(105_000L);
    }

    private void seedAccounts() {
        jdbcTemplate.update("""
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (1, 'user1', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터', '1990-01-01', 'test@test.com', '01012345678', NOW(6), NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE customer_id = customer_id
            """);

        jdbcTemplate.update("""
            INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, opened_date, created_at, updated_at)
            VALUES (301, '110333333333', 1, NULL, 'DEMAND_DEPOSIT', 100000, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '2026-08-01', NOW(6), NOW(6)),
                   (302, '110444444444', 1, NULL, 'DEMAND_DEPOSIT', 100000, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '2026-08-01', NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE balance = VALUES(balance)
            """);
    }

    private long balanceOf(long accountId) {
        Long balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
        return balance;
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
