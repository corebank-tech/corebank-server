package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.application.port.in.ProductSubscriptionDepositUseCase.ProductSubscriptionDepositCommand;
import com.shinhan.corebank.transfer.application.port.in.ProductSubscriptionDepositUseCase.ProductSubscriptionDepositResult;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * #545 재현: 상품가입 초입금이 계좌 락을 기다리는 동안 같은 출금계좌로 이체가 먼저 반영되면,
 * 원장 occurred_at 순서가 잔액 반영 순서와 같아야 한다.
 *
 * 락 순서는 계좌 락(오름차순)과 테스트가 쥔 시계로 고정한다. sleep에 기대지 않는다.
 * 스레드마다 독립 트랜잭션이 필요해 이 클래스는 @Transactional을 두지 않는다.
 */
@DisplayName("상품가입 초입금 원장 시각 — 락 대기 중 끼어든 이체와의 순서 (#545)")
class ProductSubscriptionDepositServiceConcurrencyTest extends IntegrationTestSupport {

    private static final long CUSTOMER_ID = 545L;
    // lockForTransfer는 계좌 ID 오름차순으로 잠근다. 가입 계좌를 가장 작게 둬야 상품가입이 출금계좌보다 먼저 여기서 막힌다.
    private static final long SUBSCRIPTION_ACCOUNT = 54501L;
    private static final long WITHDRAWAL_ACCOUNT = 54502L;
    private static final long PAYEE_ACCOUNT = 54503L;
    private static final String PAYEE_ACCOUNT_NUMBER = "545000000003";
    private static final long STARTING_BALANCE = 10_000L;
    private static final long TRANSFER_AMOUNT = 2_000L;
    private static final long SUBSCRIPTION_AMOUNT = 1_000L;
    private static final String SUBSCRIPTION_THREAD = "subscription-545";
    private static final Instant BASE = Instant.now();

    private static final SteppingClock CLOCK = new SteppingClock(ZoneId.of("Asia/Seoul"));

    @TestBean
    private Clock clock;

    static Clock clock() {
        return CLOCK;
    }

    @Autowired
    private ProductSubscriptionDepositService productSubscriptionDepositService;

    @Autowired
    private TransferExecutionService transferExecutionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @BeforeEach
    void setUp() {
        cleanUp();
        CLOCK.set(BASE);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("락 대기 중 이체가 먼저 반영되면 상품가입 원장 시각이 이체보다 늦고, 시각순 마지막 원장 잔액이 계좌 잔액과 같다")
    void subscriptionLedgerTime_followsBalanceOrder_whenTransferCutsInWhileWaitingForLock() throws Exception {
        // given
        seed();
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService holderPool = Executors.newSingleThreadExecutor();
        ExecutorService subscriptionPool = Executors.newSingleThreadExecutor(r -> new Thread(r, SUBSCRIPTION_THREAD));

        try {
            // 가입 계좌 행 락을 테스트가 쥔다 — 상품가입은 이 락에서 기다리게 된다
            Future<?> holder =
                    holderPool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        jdbcTemplate.queryForObject(
                                "SELECT account_id FROM account WHERE account_id = ? FOR UPDATE",
                                Long.class,
                                SUBSCRIPTION_ACCOUNT);
                        lockHeld.countDown();
                        await(releaseLock);
                    }));
            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            // when: 상품가입이 시계를 처음 읽은 뒤(= 시각을 찍은 뒤) 가입 계좌 락에서 막힌다
            CountDownLatch subscriptionReadClock = CLOCK.watchFirstReadBy(SUBSCRIPTION_THREAD);
            Future<ProductSubscriptionDepositResult> subscription = subscriptionPool.submit(
                    () -> productSubscriptionDepositService.deposit(new ProductSubscriptionDepositCommand(
                            WITHDRAWAL_ACCOUNT, SUBSCRIPTION_ACCOUNT, SUBSCRIPTION_AMOUNT)));
            assertThat(subscriptionReadClock.await(5, TimeUnit.SECONDS)).isTrue();

            // when: 그 사이 같은 출금계좌의 이체가 먼저 락을 얻어 반영·커밋된다
            CLOCK.set(BASE.plusMillis(50));
            TransferResult transfer = transferExecutionService.execute(transferCommand());
            assertThat(transfer.status()).isEqualTo(ProcessResultStatus.SUCCESS);

            // when: 락을 풀면 상품가입이 이어서 반영된다
            CLOCK.set(BASE.plusMillis(100));
            releaseLock.countDown();
            holder.get(5, TimeUnit.SECONDS);
            ProductSubscriptionDepositResult result = subscription.get(10, TimeUnit.SECONDS);

            // then: 잔액은 이체 → 상품가입 순으로 반영됐다
            assertThat(result.withdrawalBalanceAfter())
                    .isEqualTo(STARTING_BALANCE - TRANSFER_AMOUNT - SUBSCRIPTION_AMOUNT);

            // then: 커밋된 행을 DB에서 다시 읽어 원장 시각도 같은 순서인지 보고,
            // 적수·거래내역처럼 occurred_at 순으로 고른 마지막 원장 잔액이 계좌 잔액과 같은지 본다
            LocalDateTime transferAt = occurredAt(transfer.transactionNumber());
            LocalDateTime subscriptionAt = occurredAt(result.transactionNumber());
            Long lastBalanceAfter = jdbcTemplate.queryForObject(
                    """
                    SELECT balance_after FROM ledger_entry
                    WHERE account_id = ?
                    ORDER BY occurred_at DESC, ledger_entry_id DESC
                    LIMIT 1
                    """,
                    Long.class,
                    WITHDRAWAL_ACCOUNT);
            long accountBalance = balanceOf(WITHDRAWAL_ACCOUNT);
            assertSoftly(softly -> {
                softly.assertThat(subscriptionAt).isAfter(transferAt);
                softly.assertThat(lastBalanceAfter).isEqualTo(accountBalance);
            });
        } finally {
            releaseLock.countDown();
            holderPool.shutdownNow();
            subscriptionPool.shutdownNow();
            holderPool.awaitTermination(5, TimeUnit.SECONDS);
            subscriptionPool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private TransferCommand transferCommand() {
        return TransferCommand.builder()
                .customerId(CUSTOMER_ID)
                .authToken("dummy-auth-token")
                .otpAuthToken("dummy-otp-token")
                .withdrawalAccountId(WITHDRAWAL_ACCOUNT)
                .depositAccountNumber(PAYEE_ACCOUNT_NUMBER)
                .amount(TRANSFER_AMOUNT)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .myPassbookMemo("545재현")
                .recipientPassbookMemo("545재현")
                .build();
    }

    private void seed() {
        jdbcTemplate.update(
                """
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (?, 'issue545', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터', '1990-01-01', 'issue545@test.com', '01054500000', NOW(6), NOW(6), NOW(6))
            """,
                CUSTOMER_ID);

        // 없으면 이체 경로가 LMT9001로 거부된다(#388).
        jdbcTemplate.update(
                """
            INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, created_at, updated_at)
            VALUES (?, 1000000, 5000000, NOW(6), NOW(6))
            """,
                CUSTOMER_ID);

        // 가입 계좌는 입출금으로 둔다 — deposit()은 입금계좌 종류를 보지 않고, 예·적금은 상품·만기일 시드가 따로 필요하다.
        jdbcTemplate.update(
                """
            INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at)
            VALUES (?, '545000000001', ?, NULL, 'DEMAND_DEPOSIT', 0, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', FALSE, NULL, '2026-08-01', NOW(6), NOW(6)),
                   (?, '545000000002', ?, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01', NOW(6), NOW(6)),
                   (?, ?, ?, NULL, 'DEMAND_DEPOSIT', 0, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01', NOW(6), NOW(6))
            """,
                SUBSCRIPTION_ACCOUNT,
                CUSTOMER_ID,
                WITHDRAWAL_ACCOUNT,
                CUSTOMER_ID,
                STARTING_BALANCE,
                PAYEE_ACCOUNT,
                PAYEE_ACCOUNT_NUMBER,
                CUSTOMER_ID);
    }

    private void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM ledger_entry WHERE account_id IN (?, ?, ?)",
                SUBSCRIPTION_ACCOUNT,
                WITHDRAWAL_ACCOUNT,
                PAYEE_ACCOUNT);
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = ?", WITHDRAWAL_ACCOUNT);
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM transfer_limit_history WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM transfer_limit WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update(
                "DELETE FROM account WHERE account_id IN (?, ?, ?)",
                SUBSCRIPTION_ACCOUNT,
                WITHDRAWAL_ACCOUNT,
                PAYEE_ACCOUNT);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    private LocalDateTime occurredAt(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT occurred_at FROM ledger_entry WHERE account_id = ? AND transaction_number = ?",
                LocalDateTime.class,
                WITHDRAWAL_ACCOUNT,
                transactionNumber);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /** 테스트가 시각을 정하는 시계. 지정한 스레드가 처음 읽는 순간을 알려 준다. */
    private static final class SteppingClock extends Clock {

        private final ZoneId zone;
        private volatile Instant now = Instant.now();
        private volatile String watchedThread;
        private volatile CountDownLatch firstRead = new CountDownLatch(0);

        SteppingClock(ZoneId zone) {
            this.zone = zone;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        CountDownLatch watchFirstReadBy(String threadName) {
            this.firstRead = new CountDownLatch(1);
            this.watchedThread = threadName;
            return firstRead;
        }

        @Override
        public Instant instant() {
            if (Thread.currentThread().getName().equals(watchedThread)) {
                firstRead.countDown();
            }
            return now;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        // 다른 시간대로 읽는 쪽도 테스트가 정한 시각을 따르도록 같은 시계를 바라보게 한다.
        @Override
        public Clock withZone(ZoneId otherZone) {
            SteppingClock outer = this;
            return new Clock() {
                @Override
                public Instant instant() {
                    return outer.instant();
                }

                @Override
                public ZoneId getZone() {
                    return otherZone;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return outer.withZone(zone);
                }
            };
        }
    }
}
