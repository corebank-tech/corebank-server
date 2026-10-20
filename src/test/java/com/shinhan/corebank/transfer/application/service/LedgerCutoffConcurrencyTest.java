package com.shinhan.corebank.transfer.application.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.transfer.api.DepositChannel;
import com.shinhan.corebank.transfer.api.DepositType;
import com.shinhan.corebank.transfer.api.LedgerDepositUseCase;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 원장(ledger_entry)을 쓰는 거래와 영업일 전환이 경합할 때 cutoff 계약을 확인한다 (#556).
 * 커밋 순서가 검증 대상이라 @Transactional을 두지 않는다.
 */
@DisplayName("영업일 cutoff — 실제 원장 쓰기와의 경합")
class LedgerCutoffConcurrencyTest extends IntegrationTestSupport {

    // 시드·다른 테스트와 겹치지 않게 이슈 번호 대역을 쓴다.
    private static final long CUSTOMER_ID = 556L;
    private static final long PRODUCT_ACCOUNT = 55602L;
    private static final long OPENING_LEDGER_ID = 556_000_000_001L;
    private static final long OPENING_BALANCE = 1_000_000L;
    // 오늘 이전이어야 영업일 전환이 미래 영업일 가드에 걸리지 않는다.
    private static final LocalDate BASE = LocalDate.of(2026, 10, 14);

    @Autowired
    LedgerDepositUseCase ledgerDepositUseCase;

    @Autowired
    BusinessDateAdvancer advancer;

    @Autowired
    BusinessDateProvider provider;

    @Autowired
    BusinessDateTestFixture fixture;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private ExecutorService executor;
    private LocalDate original;
    private LocalDate next;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update(
                """
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (?, 'issue556', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터', '1990-01-01', 'issue556@test.com', '01055600000', NOW(6), NOW(6), NOW(6))
            """,
                CUSTOMER_ID);
        jdbc.update(
                """
            INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at)
            VALUES (?, '556000000002', ?, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', FALSE, NULL, '2026-08-01', NOW(6), NOW(6))
            """,
                PRODUCT_ACCOUNT,
                CUSTOMER_ID,
                OPENING_BALANCE);
        jdbc.update(
                """
            INSERT INTO ledger_entry (ledger_entry_id, account_id, transfer_id, transaction_number, direction, amount, balance_after,
                                      transaction_type, transaction_content, channel, reversed, reversal_id, occurred_at)
            VALUES (?, ?, NULL, '20260901BT9556000001', 'DEPOSIT', ?, ?, 'OPENING', '개시', 'BT', FALSE, NULL, '2026-09-01 00:00:00')
            """,
                OPENING_LEDGER_ID,
                PRODUCT_ACCOUNT,
                OPENING_BALANCE,
                OPENING_BALANCE);
        original = fixture.currentInDb();
        fixture.moveTo(BASE);
        next = provider.nextBusinessDay(BASE);
        jdbc.update("DELETE FROM business_date_cutoff");
        tx = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(24);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        fixture.moveTo(original);
        jdbc.update("DELETE FROM business_date_cutoff");
        cleanUp();
    }

    @Test
    @DisplayName("cutoff 를 처음 본 스냅샷에는 그 영업일로 기표된 원장이 모두 보이고, 이후 기표는 다음 영업일이다")
    void whenCutoffIsVisibleAllLedgerRowsOfThatDayAreVisible() throws Exception {
        List<Future<LocalDate>> writers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long startDelay = i * 40L;
            writers.add(executor.submit(() -> {
                Thread.sleep(startDelay);
                return tx.execute(status -> {
                    LocalDate tradeDate = ledgerDepositUseCase
                            .credit(PRODUCT_ACCOUNT, 1_000L, DepositType.INTEREST, DepositChannel.BT)
                            .tradeDate();
                    // 커밋 직전까지 gate 와 계좌 락을 쥐고 있어 전환이 이 거래를 기다려야 한다
                    sleepQuietly(100);
                    return tradeDate;
                });
            }));
        }

        AtomicReference<Long> visibleAtCutoff = new AtomicReference<>();
        Future<?> observer = executor.submit(() -> {
            while (visibleAtCutoff.get() == null) {
                tx.executeWithoutResult(status -> {
                    Integer closed = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM business_date_cutoff WHERE base_date = ?", Integer.class, BASE);
                    if (closed != null && closed == 1) {
                        // 같은 트랜잭션의 두 번째 읽기는 cutoff 를 본 스냅샷과 같다
                        visibleAtCutoff.set(interestRowsOn(BASE));
                    }
                });
                sleepQuietly(5);
            }
        });

        Thread.sleep(300);
        Future<LocalDate> advance = executor.submit(() -> advancer.advanceFrom(BASE));

        List<LocalDate> tradeDates = new ArrayList<>();
        for (Future<LocalDate> writer : writers) {
            tradeDates.add(writer.get(60, SECONDS));
        }
        assertThat(advance.get(60, SECONDS)).isEqualTo(next);
        observer.get(60, SECONDS);

        // 두 날짜가 모두 나와야 경합이 실제로 일어난 테스트다
        assertThat(tradeDates).contains(BASE, next);
        assertThat(tradeDates).allMatch(date -> date.equals(BASE) || date.equals(next));
        long baseRows = interestRowsOn(BASE);
        assertThat(baseRows).isEqualTo(tradeDates.stream().filter(BASE::equals).count());
        assertThat(interestRowsOn(next))
                .isEqualTo(tradeDates.stream().filter(next::equals).count());
        assertThat(visibleAtCutoff.get()).isEqualTo(baseRows);
        // 반환한 거래일과 원장에 저장된 거래일이 같다
        assertThat(interestRowsOn(BASE) + interestRowsOn(next)).isEqualTo(20L);
    }

    private long interestRowsOn(LocalDate tradeDate) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id = ? AND transaction_type = 'INTEREST' AND trade_date = ?",
                Long.class,
                PRODUCT_ACCOUNT,
                Date.valueOf(tradeDate));
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM ledger_entry WHERE account_id = ?", PRODUCT_ACCOUNT);
        jdbc.update("DELETE FROM account WHERE account_id = ?", PRODUCT_ACCOUNT);
        jdbc.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
