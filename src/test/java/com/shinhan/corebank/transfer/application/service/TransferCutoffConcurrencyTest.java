package com.shinhan.corebank.transfer.application.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.business.api.BusinessDateAdvancer;
import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.business.api.BusinessDateProvider;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEventSink;
import com.shinhan.corebank.gl.api.JournalPostingUseCase;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.api.TransferPreCheck;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferCommand;
import com.shinhan.corebank.transfer.application.port.in.InterbankTransferResult;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankBankPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankGatewayPort;
import com.shinhan.corebank.transfer.application.port.out.InterbankOutcome;
import com.shinhan.corebank.transfer.application.port.out.InterbankReply;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.transfer.application.port.out.TransferOtpVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 이체가 계좌 락을 기다리는 사이 영업일 전환이 시도되는 경합(#662, #648). 락 대기가 검증 대상이라 @Transactional을 두지 않는다.
@DisplayName("영업일 cutoff — 이체가 계좌 락을 기다리는 사이의 영업일 전환")
class TransferCutoffConcurrencyTest extends IntegrationTestSupport {

    // 시드·다른 테스트와 겹치지 않게 이슈 번호 대역을 쓴다.
    private static final long CUSTOMER_ID = 662L;
    private static final long WITHDRAWAL_ACCOUNT = 66201L;
    private static final long DEPOSIT_ACCOUNT = 66202L;
    private static final String DEPOSIT_ACCOUNT_NUMBER = "662000000002";
    private static final long OPENING_BALANCE = 1_000_000L;
    private static final long AMOUNT = 10_000L;
    // 오늘 이전이어야 영업일 전환이 미래 영업일 가드에 걸리지 않는다.
    private static final LocalDate BASE = LocalDate.of(2026, 10, 14);

    // 다른 이체 통합 테스트와 같은 경계 대역 — 같은 스프링 컨텍스트를 재사용한다.
    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @MockitoBean
    private DomainEventSink domainEventSink;

    @Autowired
    private TransferExecutionUseCase transferExecutionUseCase;

    @Autowired
    private InterbankSettlementService interbankSettlementService;

    @Autowired
    private AccountLockPort accountLockPort;

    @Autowired
    private InterbankBankPort interbankBankPort;

    @Autowired
    private TransferLimitPort transferLimitPort;

    @Autowired
    private TransferAuthTokenVerificationPort transferAuthTokenVerificationPort;

    @Autowired
    private TransferOtpVerificationPort transferOtpVerificationPort;

    @Autowired
    private TransferSequencePort transferSequencePort;

    @Autowired
    private TransferSavePort transferSavePort;

    @Autowired
    private LedgerSavePort ledgerSavePort;

    @Autowired
    private JournalPostingUseCase journalPostingUseCase;

    @Autowired
    private List<TransferPreCheck> transferPreChecks;

    @Autowired
    private BusinessDateGate businessDateGate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private Clock clock;

    @Autowired
    private BusinessDateAdvancer advancer;

    @Autowired
    private BusinessDateProvider provider;

    @Autowired
    private BusinessDateTestFixture fixture;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private ExecutorService executor;
    private CountDownLatch releaseAccountLock;
    private LocalDate original;
    private LocalDate next;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update(
                """
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (?, 'issue662', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터', '1990-01-01', 'issue662@test.com', '01066200000', NOW(6), NOW(6), NOW(6))
            """,
                CUSTOMER_ID);
        jdbc.update(
                """
            INSERT INTO transfer_limit (customer_id, one_time_limit, daily_limit, created_at, updated_at)
            VALUES (?, 1000000, 5000000, NOW(6), NOW(6))
            """,
                CUSTOMER_ID);
        jdbc.update(
                """
            INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at)
            VALUES (?, '662000000001', ?, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01', NOW(6), NOW(6)),
                   (?, ?, ?, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', FALSE, NULL, '2026-08-01', NOW(6), NOW(6))
            """,
                WITHDRAWAL_ACCOUNT,
                CUSTOMER_ID,
                OPENING_BALANCE,
                DEPOSIT_ACCOUNT,
                DEPOSIT_ACCOUNT_NUMBER,
                CUSTOMER_ID,
                OPENING_BALANCE);
        original = fixture.currentInDb();
        fixture.moveTo(BASE);
        next = provider.nextBusinessDay(BASE);
        jdbc.update("DELETE FROM business_date_cutoff");
        tx = new TransactionTemplate(transactionManager);
        executor = Executors.newFixedThreadPool(4);
        releaseAccountLock = new CountDownLatch(1);
    }

    @AfterEach
    void tearDown() {
        // 계좌 락을 쥔 스레드가 남지 않게 먼저 풀고 끝낸다
        releaseAccountLock.countDown();
        executor.shutdownNow();
        fixture.moveTo(original);
        jdbc.update("DELETE FROM business_date_cutoff");
        cleanUp();
    }

    @Test
    @DisplayName("당행 이체가 계좌 락을 기다리는 동안 영업일 전환은 이체가 끝나길 기다리고, 이체는 전환 전 영업일로 기표된다")
    void domesticTransferWaitingForAccountLockBlocksBusinessDateAdvance() throws Exception {
        Future<?> lockHolder = holdWithdrawalAccountLock();
        Future<TransferResult> transfer = executor.submit(() -> transferExecutionUseCase.execute(domesticCommand()));
        // 이체가 gate 공유 락을 쥔 채 계좌 락에서 막힌 상태
        awaitGateHeldByOthers();

        Future<LocalDate> advance = executor.submit(() -> advancer.advanceFrom(BASE));
        // 전환이 gate 배타 락에서 이체 뒤에 줄을 선 상태 — 예전 코드는 gate가 없어 여기서 전환이 먼저 끝났다
        sleepQuietly(500);
        assertThat(advance.isDone()).isFalse();
        assertThat(transfer.isDone()).isFalse();

        releaseAccountLock.countDown();
        lockHolder.get(30, SECONDS);
        TransferResult result = transfer.get(60, SECONDS);
        assertThat(advance.get(60, SECONDS)).isEqualTo(next);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        String transactionNumber = result.transactionNumber();
        assertThat(jdbc.queryForObject(
                        "SELECT trade_date FROM transfer WHERE transaction_number = ?",
                        LocalDate.class,
                        transactionNumber))
                .isEqualTo(BASE);
        assertThat(jdbc.queryForList(
                        "SELECT trade_date FROM ledger_entry WHERE transaction_number = ?",
                        LocalDate.class,
                        transactionNumber))
                .containsExactly(BASE, BASE);
        assertThat(jdbc.queryForList(
                        "SELECT trade_date FROM gl_voucher WHERE reference_key = ?",
                        LocalDate.class,
                        transactionNumber))
                .isNotEmpty()
                .containsOnly(BASE);
        // 전환은 이체가 커밋된 뒤에 BASE 를 마감하고 다음 영업일로 넘어간다
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM business_date_cutoff WHERE base_date = ?", Integer.class, BASE))
                .isEqualTo(1);
        assertThat(fixture.currentInDb()).isEqualTo(next);
    }

    @Test
    @DisplayName("타행 이체 출금이 계좌 락을 기다리는 동안 영업일 전환은 출금이 끝나길 기다리고, 출금은 전환 전 영업일로 기표된다")
    void interbankDebitWaitingForAccountLockBlocksBusinessDateAdvance() throws Exception {
        InterbankGatewayPort gateway = mock(InterbankGatewayPort.class);
        when(gateway.isAvailable()).thenReturn(true);
        // 응답을 못 받아 TIMEOUT 으로 남는다 — 이 테스트의 대상은 확정이 아니라 출금(debitAndHold)이다
        when(gateway.send(org.mockito.ArgumentMatchers.any()))
                .thenReturn(InterbankReply.of(InterbankOutcome.UNKNOWN, null));

        Future<?> lockHolder = holdWithdrawalAccountLock();
        Future<InterbankTransferResult> execute =
                executor.submit(() -> interbankService(gateway).execute(interbankCommand()));
        awaitGateHeldByOthers();

        Future<LocalDate> advance = executor.submit(() -> advancer.advanceFrom(BASE));
        sleepQuietly(500);
        assertThat(advance.isDone()).isFalse();
        assertThat(execute.isDone()).isFalse();

        releaseAccountLock.countDown();
        lockHolder.get(30, SECONDS);
        InterbankTransferResult result = execute.get(60, SECONDS);
        assertThat(advance.get(60, SECONDS)).isEqualTo(next);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.TIMEOUT);
        String transactionNumber = result.transactionNumber();
        assertThat(jdbc.queryForObject(
                        "SELECT trade_date FROM transfer WHERE transaction_number = ?",
                        LocalDate.class,
                        transactionNumber))
                .isEqualTo(BASE);
        // 출금 1행
        assertThat(jdbc.queryForList(
                        "SELECT trade_date FROM ledger_entry WHERE transaction_number = ?",
                        LocalDate.class,
                        transactionNumber))
                .containsExactly(BASE);
        assertThat(jdbc.queryForList(
                        "SELECT trade_date FROM gl_voucher WHERE reference_key = ?",
                        LocalDate.class,
                        transactionNumber))
                .isNotEmpty()
                .containsOnly(BASE);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM business_date_cutoff WHERE base_date = ?", Integer.class, BASE))
                .isEqualTo(1);
        assertThat(fixture.currentInDb()).isEqualTo(next);
    }

    @Test
    @DisplayName("타행 이체 확정이 계좌 락을 기다리는 동안 영업일 전환은 확정이 끝나길 기다리고, 환급은 전환 전 영업일로 기표된다")
    void interbankSettlementWaitingForAccountLockBlocksBusinessDateAdvance() throws Exception {
        InterbankGatewayPort gateway = mock(InterbankGatewayPort.class);
        when(gateway.isAvailable()).thenReturn(true);
        // 응답을 못 받아 TIMEOUT 으로 남기고, 확정은 아래에서 따로 부른다
        when(gateway.send(org.mockito.ArgumentMatchers.any()))
                .thenReturn(InterbankReply.of(InterbankOutcome.UNKNOWN, null));
        InterbankTransferResult executed = interbankService(gateway).execute(interbankCommand());
        assertThat(executed.status()).isEqualTo(ProcessResultStatus.TIMEOUT);
        String transactionNumber = executed.transactionNumber();

        Future<?> lockHolder = holdWithdrawalAccountLock();
        // 환급(NOT_PROCESSED)은 출금계좌 락을 잡으므로 이체 행 락과 gate 를 쥔 채 막힌다
        Future<?> settle = executor.submit(() -> interbankSettlementService.settle(
                transactionNumber, InterbankReply.of(InterbankOutcome.NOT_PROCESSED, null)));
        awaitGateHeldByOthers();

        Future<LocalDate> advance = executor.submit(() -> advancer.advanceFrom(BASE));
        sleepQuietly(500);
        assertThat(advance.isDone()).isFalse();
        assertThat(settle.isDone()).isFalse();

        releaseAccountLock.countDown();
        lockHolder.get(30, SECONDS);
        settle.get(60, SECONDS);
        assertThat(advance.get(60, SECONDS)).isEqualTo(next);

        assertThat(jdbc.queryForObject(
                        "SELECT trade_date FROM transfer WHERE transaction_number = ?",
                        LocalDate.class,
                        transactionNumber))
                .isEqualTo(BASE);
        // 출금 1행 + 환급 1행
        assertThat(jdbc.queryForList(
                        "SELECT trade_date FROM ledger_entry WHERE transaction_number = ?",
                        LocalDate.class,
                        transactionNumber))
                .containsExactly(BASE, BASE);
        assertThat(jdbc.queryForList(
                        "SELECT trade_date FROM gl_voucher WHERE reference_key = ?",
                        LocalDate.class,
                        transactionNumber))
                .isNotEmpty()
                .containsOnly(BASE);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM business_date_cutoff WHERE base_date = ?", Integer.class, BASE))
                .isEqualTo(1);
        assertThat(fixture.currentInDb()).isEqualTo(next);
    }

    // 출금계좌 행에 배타 락을 잡고 releaseAccountLock 이 풀릴 때까지 놓지 않는다
    private Future<?> holdWithdrawalAccountLock() throws InterruptedException {
        CountDownLatch locked = new CountDownLatch(1);
        Future<?> holder = executor.submit(() -> tx.executeWithoutResult(status -> {
            jdbc.queryForObject(
                    "SELECT account_id FROM account WHERE account_id = ? FOR UPDATE", Long.class, WITHDRAWAL_ACCOUNT);
            locked.countDown();
            awaitQuietly(releaseAccountLock);
        }));
        assertThat(locked.await(10, SECONDS)).isTrue();
        return holder;
    }

    // 다른 트랜잭션이 gate 공유 락을 쥘 때까지 기다린다 — 배타 락을 NOWAIT 로 시도해 실패하면 쥔 것이다
    private void awaitGateHeldByOthers() {
        long deadline = System.nanoTime() + SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (!canLockGateExclusively()) {
                return;
            }
            sleepQuietly(20);
        }
        throw new AssertionError("이체가 gate 공유 락을 쥐지 않았다");
    }

    private boolean canLockGateExclusively() {
        try {
            tx.executeWithoutResult(status -> jdbc.queryForObject(
                    "SELECT business_date FROM business_date WHERE date_type = 'BUSINESS_DATE' FOR UPDATE NOWAIT",
                    LocalDate.class));
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }

    private InterbankTransferService interbankService(InterbankGatewayPort gateway) {
        return new InterbankTransferService(
                accountLockPort,
                interbankBankPort,
                gateway,
                interbankSettlementService,
                transferLimitPort,
                transferAuthTokenVerificationPort,
                transferOtpVerificationPort,
                transferSequencePort,
                transferSavePort,
                ledgerSavePort,
                journalPostingUseCase,
                transferPreChecks,
                businessDateGate,
                eventPublisher,
                clock,
                transactionManager);
    }

    private static TransferCommand domesticCommand() {
        return TransferCommand.builder()
                .customerId(CUSTOMER_ID)
                .authToken("dummy-auth-token")
                .otpAuthToken("dummy-otp-token")
                .withdrawalAccountId(WITHDRAWAL_ACCOUNT)
                .depositAccountNumber(DEPOSIT_ACCOUNT_NUMBER)
                .amount(AMOUNT)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .build();
    }

    private static InterbankTransferCommand interbankCommand() {
        return InterbankTransferCommand.builder()
                .customerId(CUSTOMER_ID)
                .withdrawalAccountId(WITHDRAWAL_ACCOUNT)
                .payeeBankCode("004")
                .depositAccountNumber("1234567890123")
                .amount(AMOUNT)
                .myPassbookMemo("생활비")
                .recipientPassbookMemo("홍길동")
                .authToken("account-password-token")
                .otpAuthToken("otp-token")
                .build();
    }

    private void cleanUp() {
        jdbc.update(
                """
            DELETE e FROM gl_journal_entry e JOIN gl_voucher v ON v.voucher_no = e.voucher_no
            WHERE v.reference_key IN (SELECT transaction_number FROM transfer WHERE withdrawal_account_id = ?)
            """,
                WITHDRAWAL_ACCOUNT);
        jdbc.update(
                "DELETE FROM gl_voucher WHERE reference_key IN (SELECT transaction_number FROM transfer WHERE withdrawal_account_id = ?)",
                WITHDRAWAL_ACCOUNT);
        jdbc.update("DELETE FROM ledger_entry WHERE account_id IN (?, ?)", WITHDRAWAL_ACCOUNT, DEPOSIT_ACCOUNT);
        jdbc.update("DELETE FROM transfer WHERE withdrawal_account_id = ?", WITHDRAWAL_ACCOUNT);
        jdbc.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = ?", CUSTOMER_ID);
        jdbc.update("DELETE FROM account WHERE account_id IN (?, ?)", WITHDRAWAL_ACCOUNT, DEPOSIT_ACCOUNT);
        jdbc.update("DELETE FROM transfer_limit WHERE customer_id = ?", CUSTOMER_ID);
        jdbc.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(60, SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
