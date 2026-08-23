package com.shinhan.corebank.transfer.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-TRSF-015 서비스 레벨 재현: AccountLockDeadlockAvoidanceTest가 락 획득만(어댑터 레벨)
 * 검증한다면, 이 테스트는 채번·원장 2행 저장·완료 기록까지 포함한 TransferExecutionService의
 * execute() 전체를 다수 스레드로 동시 실행해도 안전한지 증명한다.
 * 스레드별로 독립된 트랜잭션/커넥션이 필요하므로 이 테스트 클래스는 @Transactional을 두지 않는다.
 */
@DisplayName("TransferExecutionService 동시 이체 스모크 테스트")
class TransferExecutionServiceConcurrencyTest extends IntegrationTestSupport {

    private static final long ACCOUNT_A = 401L;
    private static final long ACCOUNT_B = 402L;
    private static final String ACCOUNT_A_NUMBER = "110555555555";
    private static final String ACCOUNT_B_NUMBER = "110666666666";
    private static final int ROUNDS_PER_DIRECTION = 30;
    private static final long AMOUNT = 1_000L;
    private static final long STARTING_BALANCE = 200_000L;

    @Autowired
    private TransferExecutionService transferExecutionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 이체 실행은 실제 OTP 발급 없이 otp.api 경계만 검증한다 — OTP 자체의 발급/소비 로직은
    // otp 도메인 테스트가 담당한다. Mockito void mock은 기본이 no-op이라 별도 stubbing 없이도 통과시킨다.
    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (?, ?)", ACCOUNT_A, ACCOUNT_B);
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id IN (?, ?)", ACCOUNT_A, ACCOUNT_B);
        jdbcTemplate.update("DELETE FROM account WHERE account_id IN (?, ?)", ACCOUNT_A, ACCOUNT_B);
    }

    @Test
    @DisplayName("A→B, B→A 교차 이체를 다건 동시 실행해도 데드락 없이 전부 성공하고 원장이 정합한다")
    void concurrentCrossTransfers_allSucceed_withConsistentLedger() throws Exception {
        // given
        seedAccounts();
        int totalTransfers = ROUNDS_PER_DIRECTION * 2;

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < ROUNDS_PER_DIRECTION; i++) {
                futures.add(pool.submit(callable(start, ACCOUNT_A, ACCOUNT_B_NUMBER, AMOUNT)));
                futures.add(pool.submit(callable(start, ACCOUNT_B, ACCOUNT_A_NUMBER, AMOUNT)));
            }

            // when
            start.countDown();
            List<TransferResult> results = new ArrayList<>();
            for (Future<TransferResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            // then: 데드락으로 인한 실패 없이 전부 SUCCESS
            assertThat(results).hasSize(totalTransfers);
            assertThat(results).allSatisfy(r -> assertThat(r.status()).isEqualTo(ProcessResultStatus.SUCCESS));

            // then: 동시 채번에도 거래번호 중복이 없다
            Set<String> transactionNumbers = results.stream()
                    .map(TransferResult::transactionNumber)
                    .collect(Collectors.toSet());
            assertThat(transactionNumbers).hasSize(totalTransfers);

            // then: 양방향 건수·금액이 같으므로 최종 잔액은 시작 잔액과 같다
            assertThat(balanceOf(ACCOUNT_A)).isEqualTo(STARTING_BALANCE);
            assertThat(balanceOf(ACCOUNT_B)).isEqualTo(STARTING_BALANCE);

            // then: 원장은 이체 1건당 2행, 입금합 == 출금합(복식부기 정합성)
            Long ledgerRowCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (?, ?)",
                    Long.class, ACCOUNT_A, ACCOUNT_B);
            assertThat(ledgerRowCount).isEqualTo(totalTransfers * 2L);

            Long depositSum = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM ledger_entry WHERE account_id IN (?, ?) AND direction = 'DEPOSIT'",
                    Long.class, ACCOUNT_A, ACCOUNT_B);
            Long withdrawalSum = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM ledger_entry WHERE account_id IN (?, ?) AND direction = 'WITHDRAWAL'",
                    Long.class, ACCOUNT_A, ACCOUNT_B);
            assertThat(depositSum).isEqualTo(withdrawalSum);

            // then: 거래번호별로도 DEPOSIT 1행 + WITHDRAWAL 1행이 정확히 존재한다.
            // (합계만 비교하면 한 이체의 출금 행이 유실되고 다른 이체의 입금 행이 중복돼도
            // 총합은 우연히 같아질 수 있어, 건별 짝을 직접 확인한다.)
            Long unbalancedTransactionCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM (
                        SELECT transaction_number
                        FROM ledger_entry
                        WHERE account_id IN (?, ?)
                        GROUP BY transaction_number
                        HAVING SUM(direction = 'DEPOSIT') <> 1 OR SUM(direction = 'WITHDRAWAL') <> 1
                    ) unbalanced
                    """, Long.class, ACCOUNT_A, ACCOUNT_B);
            assertThat(unbalancedTransactionCount).isZero();
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Callable<TransferResult> callable(CountDownLatch start, long withdrawalAccountId, String depositAccountNumber, long amount) {
        return () -> {
            await(start);
            TransferCommand command = TransferCommand.builder()
                    .customerId(1L)
                    .authToken("dummy-auth-token")
                    .otpAuthToken("dummy-otp-token")
                    .withdrawalAccountId(withdrawalAccountId)
                    .depositAccountNumber(depositAccountNumber)
                    .amount(amount)
                    .transferType(TransferType.IMMEDIATE)
                    .channel(TransferChannel.WB)
                    .myPassbookMemo("동시성테스트")
                    .recipientPassbookMemo("동시성테스트")
                    .build();
            return transferExecutionService.execute(command);
        };
    }

    private void seedAccounts() {
        jdbcTemplate.update("""
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (1, 'user1', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터', '1990-01-01', 'test@test.com', '01012345678', NOW(6), NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE customer_id = customer_id
            """);

        jdbcTemplate.update("""
            INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at)
            VALUES (?, ?, 1, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01', NOW(6), NOW(6)),
                   (?, ?, 1, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01', NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE balance = VALUES(balance)
            """, ACCOUNT_A, ACCOUNT_A_NUMBER, STARTING_BALANCE, ACCOUNT_B, ACCOUNT_B_NUMBER, STARTING_BALANCE);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
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
