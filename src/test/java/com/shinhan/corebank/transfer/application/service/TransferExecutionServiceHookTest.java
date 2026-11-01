package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.LedgerPostingContext;
import com.shinhan.corebank.transfer.api.LedgerPostingHook;
import com.shinhan.corebank.transfer.api.RecentTransferQuery;
import com.shinhan.corebank.transfer.api.TransferHeld;
import com.shinhan.corebank.transfer.api.TransferHoldContext;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.api.TransferHoldPolicy;
import com.shinhan.corebank.transfer.api.TransferPreCheck;
import com.shinhan.corebank.transfer.api.TransferPreCheckContext;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.transfer.application.port.out.TransferOtpVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferHold;
import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 이체 확장점(PH-99) 훅이 execute()의 약속한 자리에서, 약속한 트랜잭션 규칙대로 호출되는지 검증한다.
 *
 * 테스트용 훅을 빈으로 등록하면 같은 컨텍스트를 쓰는 다른 이체 테스트까지 영향을 받으므로, 실제 포트를 주입받아 서비스를 직접 조립한다.
 * MockitoBean 구성은 TransferExecutionServiceTest와 같게 맞춰 컨텍스트 캐시를 공유한다.
 */
class TransferExecutionServiceHookTest extends IntegrationTestSupport {

    // 달력일(테스트 실행일)과 겹치지 않는 영업일 — 같으면 영업일을 읽었는지 달력일을 썼는지 구분되지 않는다.
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 11, 2);

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AccountLockPort accountLockPort;

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
    private TransferLookupPort transferLookupPort;

    @Autowired
    private LedgerSavePort ledgerSavePort;

    @Autowired
    private TransferHoldPort transferHoldPort;

    @Autowired
    private Clock clock;

    @Autowired
    private RecentTransferQuery recentTransferQuery;

    @Autowired
    private BusinessDateGate businessDateGate;

    @Autowired
    private BusinessDateTestFixture businessDateTestFixture;

    private LocalDate originalBusinessDate;

    // 이체 실행은 실제 OTP 발급 없이 otp.api 경계만 검증한다 — OTP 자체의 발급/소비 로직은
    // otp 도메인 테스트가 담당한다. Mockito void mock은 기본이 no-op이라 별도 stubbing 없이도 통과시킨다.
    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    // 계좌비밀번호 인증도 동일하게 account.api 경계만 검증한다 — 토큰 자체의 발급/소비 로직은
    // account 도메인 테스트가 담당한다.
    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @BeforeEach
    void seed() {
        originalBusinessDate = businessDateTestFixture.currentInDb();
        businessDateTestFixture.moveTo(BUSINESS_DATE);
        // 다른 테스트 클래스가 남긴 고객 1의 한도 사용액 행이 롤백 검증을 오염시키지 않게 먼저 비운다.
        cleanUpCommittedData();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
    }

    @AfterEach
    void restoreBusinessDate() {
        businessDateTestFixture.moveTo(originalBusinessDate);
    }

    @AfterEach
    void cleanUpCommittedData() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = 1");
        jdbcTemplate.update("DELETE FROM transfer_hold WHERE customer_id = 1");
    }

    @Test
    @DisplayName("[훅 A] PreCheck가 거부하면 ERROR로 확정하고 OTP·한도·잔액을 건드리지 않으며 뒤 순번은 호출하지 않는다")
    void preCheck_rejects_failsTransferBeforeOtpAndLimit() {
        List<Integer> called = new ArrayList<>();
        TransferExecutionService service = serviceWith(
                List.of(recordingPreCheck(1, called, true), recordingPreCheck(2, called, false)), List.of());

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.INSUFFICIENT_BALANCE.getCode());
        assertThat(called).containsExactly(1);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM transfer WHERE transaction_number = ?", String.class, result.transactionNumber());
        assertThat(status).isEqualTo("ERROR");
        assertThat(balanceOf(101)).isEqualTo(100000L);
        verifyNoInteractions(otpAuthTokenVerifier);
        Integer usageRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_limit_daily_usage WHERE customer_id = 1", Integer.class);
        assertThat(usageRows).isZero();
    }

    @Test
    @DisplayName("[훅 A] PreCheck는 등록 순서와 무관하게 order 오름차순으로 호출되고 이체 입력값을 받는다")
    void preChecks_runInOrder_withTransferInput() {
        List<Integer> called = new ArrayList<>();
        List<TransferPreCheckContext> received = new ArrayList<>();
        TransferPreCheck capturing = new TransferPreCheck() {
            @Override
            public int order() {
                return 20;
            }

            @Override
            public void check(TransferPreCheckContext context) {
                called.add(20);
                received.add(context);
            }
        };
        TransferExecutionService service =
                serviceWith(List.of(capturing, recordingPreCheck(10, called, false)), List.of());

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(called).containsExactly(10, 20);
        assertThat(received).containsExactly(new TransferPreCheckContext(1L, 101L, 30000L));
    }

    @Test
    @DisplayName("[훅 B] 훅이 BusinessException을 던지면 잔액·원장·한도 적립이 롤백되고 이체는 ERROR로 확정된다")
    void ledgerPostingHook_businessException_rollsBackLedgerAndFails() {
        // 오류코드 값은 무관하다. GL 기표 실패를 대신하는 대역이다.
        LedgerPostingHook failing = context -> {
            throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
        };
        TransferExecutionService service = serviceWith(List.of(), List.of(failing));

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.INSUFFICIENT_BALANCE.getCode());
        assertRolledBack();
        assertThat(transferStatuses()).containsExactly("ERROR");
    }

    @Test
    @DisplayName("[훅 B] 훅이 예상 밖 예외를 던지면 삼키지 않고 호출자에 전파하며 기표를 롤백한다")
    void ledgerPostingHook_unexpectedException_propagatesAndRollsBack() {
        IllegalStateException boom = new IllegalStateException("gl down");
        LedgerPostingHook failing = context -> {
            throw boom;
        };
        TransferExecutionService service = serviceWith(List.of(), List.of(failing));

        assertThatThrownBy(() -> service.execute(immediateCommand(30000L))).isSameAs(boom);

        assertRolledBack();
        String errorCode = jdbcTemplate.queryForObject(
                "SELECT error_code FROM transfer WHERE withdrawal_account_id = 101 AND status = 'ERROR'", String.class);
        assertThat(errorCode).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    @DisplayName("[훅 B] 훅은 활성 트랜잭션 안에서 거래번호·유형·금액·계좌·거래일을 받는다")
    void ledgerPostingHook_receivesContext_insideTransaction() {
        List<LedgerPostingContext> received = new ArrayList<>();
        List<Boolean> transactionActive = new ArrayList<>();
        LedgerPostingHook capturing = context -> {
            received.add(context);
            transactionActive.add(TransactionSynchronizationManager.isActualTransactionActive());
        };
        TransferExecutionService service = serviceWith(List.of(), List.of(capturing));

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(transactionActive).containsExactly(true);
        assertThat(received).hasSize(1);
        LedgerPostingContext context = received.get(0);
        assertThat(context.transactionNumber()).isEqualTo(result.transactionNumber());
        assertThat(context.txType()).isEqualTo("IMMEDIATE_TRANSFER");
        assertThat(context.amount()).isEqualTo(30000L);
        assertThat(context.fromAccountId()).isEqualTo(101L);
        assertThat(context.toAccountId()).isEqualTo(202L);
        assertThat(context.tradeDate()).isEqualTo(BUSINESS_DATE);
    }

    @Test
    @DisplayName("[거래일] 성공 이체는 transfer 행·원장 2행·훅 B가 모두 같은 영업일을 거래일로 갖는다")
    void success_tradeDateIsBusinessDate_onTransferLedgerAndHook() {
        List<LocalDate> hookTradeDates = new ArrayList<>();
        TransferExecutionService service =
                serviceWith(List.of(), List.of(context -> hookTradeDates.add(context.tradeDate())));

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(transferTradeDate(result.transactionNumber())).isEqualTo(BUSINESS_DATE);
        assertThat(jdbcTemplate.queryForList(
                        "SELECT trade_date FROM ledger_entry WHERE transaction_number = ?",
                        LocalDate.class,
                        result.transactionNumber()))
                .containsExactly(BUSINESS_DATE, BUSINESS_DATE);
        assertThat(hookTradeDates).containsExactly(BUSINESS_DATE);
    }

    @Test
    @DisplayName("[거래일] 실패(ERROR) 이체 행도 원장 없이 같은 영업일을 거래일로 갖는다")
    void failure_errorRowHasBusinessDateAsTradeDate() {
        TransferExecutionService service =
                serviceWith(List.of(recordingPreCheck(1, new ArrayList<>(), true)), List.of());

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(transferTradeDate(result.transactionNumber())).isEqualTo(BUSINESS_DATE);
    }

    @Test
    @DisplayName("[메트릭] execute()가 예외를 던져도 transfer.count{result=EXCEPTION}과 transfer.duration이 한 번 기록된다 (#541)")
    void execute_throws_recordsExceptionMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        LedgerPostingHook failing = context -> {
            throw new IllegalStateException("gl down");
        };
        TransferExecutionService service = serviceWith(List.of(), List.of(failing), registry);

        assertThatThrownBy(() -> service.execute(immediateCommand(30000L))).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("transfer.count")
                        .tags("result", "EXCEPTION", "replayed", "false")
                        .counter()
                        .count())
                .isEqualTo(1.0);
        assertThat(registry.get("transfer.duration")
                        .tags("result", "EXCEPTION", "replayed", "false")
                        .timer()
                        .count())
                .isEqualTo(1L);
        assertThat(registry.get("transfer.count")
                        .tags("result", "SUCCESS", "replayed", "false")
                        .counter()
                        .count())
                .isZero();
    }

    @Test
    @DisplayName("[메트릭] 이체가 한 번도 없어도 결과 6종(보류 포함) × replayed 2종의 transfer.count·transfer.duration이 0으로 등록돼 있다 (#541)")
    void construct_registersAllTransferMetricsAtZero() {
        MeterRegistry registry = new SimpleMeterRegistry();

        serviceWith(List.of(), List.of(), registry);

        assertThat(registry.find("transfer.count").counters()).hasSize(12);
        assertThat(registry.find("transfer.duration").timers()).hasSize(12);
        assertThat(registry.find("transfer.count").counters())
                .allSatisfy(counter -> assertThat(counter.count()).isZero());
    }

    @Test
    @DisplayName("[훅 C] 바로 실행을 내면 이체가 SUCCESS로 끝나고, 훅은 고객·계좌·금액·flowId·채번 시각을 받는다")
    void holdPolicy_proceeds_receivesTransferInput() {
        List<TransferHoldContext> received = new ArrayList<>();
        TransferExecutionService service = serviceWithHoldPolicies(List.of(recordingHoldPolicy(1, received, null)));

        TransferResult result = service.execute(immediateCommandWithFlowId(30000L, "flow-1"));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(received).singleElement().satisfies(context -> {
            assertThat(context.customerId()).isEqualTo(1L);
            assertThat(context.withdrawalAccountId()).isEqualTo(101L);
            assertThat(context.depositAccountNumber()).isEqualTo("110222222222");
            assertThat(context.amount()).isEqualTo(30000L);
            assertThat(context.flowId()).isEqualTo("flow-1");
            assertThat(context.requestedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("[훅 C] 등록 순서와 무관하게 order 오름차순으로 호출된다")
    void holdPolicies_runInOrder() {
        List<Integer> called = new ArrayList<>();
        TransferExecutionService service = serviceWithHoldPolicies(
                List.of(orderRecordingHoldPolicy(20, called), orderRecordingHoldPolicy(10, called)));

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(called).containsExactly(10, 20);
    }

    @Test
    @DisplayName("[훅 C] 훅이 예외를 던지면 이체는 ERROR로 확정되고 원장·잔액·한도 적립이 롤백된다")
    void holdPolicy_throws_failsTransferAndRollsBackLimit() {
        TransferHoldPolicy failing = holdPolicy(1, context -> {
            throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
        });
        TransferExecutionService service = serviceWithHoldPolicies(List.of(failing));

        TransferResult result = service.execute(immediateCommand(30000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(transferStatuses()).containsExactly("ERROR");
        assertRolledBack();
    }

    @Test
    @DisplayName("[훅 C] 보류를 내면 기표를 롤백하고, 원본 요청을 transfer_hold에 저장하며 같은 트랜잭션에서 TransferHeld를 발행한다")
    void holdPolicy_holds_savesHoldAndPublishesInSameTransaction() {
        List<Integer> called = new ArrayList<>();
        List<Object> events = new ArrayList<>();
        List<Boolean> publishedInTransaction = new ArrayList<>();
        ApplicationEventPublisher recording = event -> {
            events.add(event);
            publishedInTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            eventPublisher.publishEvent(event);
        };
        LocalDateTime releaseAt = LocalDateTime.of(2031, 7, 1, 13, 0);
        TransferHoldPolicy holding = holdPolicy(1, context -> TransferHoldDecision.holdUntil("TEST", releaseAt));
        TransferExecutionService service = serviceWith(
                List.of(),
                List.of(),
                List.of(holding, orderRecordingHoldPolicy(2, called)),
                transferHoldPort,
                recording,
                new SimpleMeterRegistry());

        TransferResult result = service.execute(immediateCommandWithFlowId(30000L, "flow-1"));

        assertThat(result.held()).isTrue();
        assertThat(result.status()).isNull();
        assertThat(result.transactionNumber()).as("판정 전에 받은 거래번호는 쓰이지 않는다").isNull();
        assertThat(result.releaseAt()).isEqualTo(releaseAt);
        assertThat(called).as("처음 보류를 낸 훅이 정하고 뒤 순번은 부르지 않는다").isEmpty();
        assertRolledBack();
        assertThat(transferStatuses()).isEmpty();

        Map<String, Object> hold = jdbcTemplate.queryForMap(
                "SELECT * FROM transfer_hold WHERE transfer_hold_id = ?", result.transferHoldId());
        assertThat(hold.get("status")).isEqualTo("HELD");
        assertThat(hold.get("customer_id")).isEqualTo(1L);
        assertThat(hold.get("withdrawal_account_id")).isEqualTo(101L);
        assertThat(hold.get("deposit_account_number")).isEqualTo("110222222222");
        assertThat(hold.get("amount")).isEqualTo(30000L);
        assertThat(hold.get("channel")).isEqualTo("WB");
        assertThat(hold.get("flow_id")).isEqualTo("flow-1");
        assertThat(hold.get("policy_code")).isEqualTo("TEST");
        assertThat(hold.get("release_mode")).isEqualTo("AT_TIME");
        assertThat(hold.get("authenticated_at")).isNotNull();

        assertThat(events).singleElement().isInstanceOfSatisfying(TransferHeld.class, held -> {
            assertThat(held.transferHoldId()).isEqualTo(result.transferHoldId());
            assertThat(held.customerId()).isEqualTo(1L);
            assertThat(held.policyCode()).isEqualTo("TEST");
            assertThat(held.releaseAt()).isEqualTo(releaseAt);
            assertThat(held.flowId()).isEqualTo("flow-1");
        });
        assertThat(publishedInTransaction).containsExactly(true);
    }

    @Test
    @DisplayName("[훅 C] 보류 저장이 실패하면 기표 중 내부 오류처럼 ERROR 행을 남기고 예외를 전파하며, 원장·한도·보류는 남지 않는다")
    void holdPolicy_holdStorageFails_recordsErrorAndRollsBack() {
        IllegalStateException boom = new IllegalStateException("hold store down");
        TransferHoldPort failingPort = new FailingSaveTransferHoldPort(transferHoldPort, boom);
        TransferHoldPolicy holding = holdPolicy(1, context -> TransferHoldDecision.holdForApproval("TEST"));
        TransferExecutionService service = serviceWith(
                List.of(), List.of(), List.of(holding), failingPort, eventPublisher, new SimpleMeterRegistry());

        assertThatThrownBy(() -> service.execute(immediateCommand(30000L))).isSameAs(boom);

        assertRolledBack();
        assertThat(transferStatuses()).containsExactly("ERROR");
        String errorCode = jdbcTemplate.queryForObject(
                "SELECT error_code FROM transfer WHERE withdrawal_account_id = 101 AND status = 'ERROR'", String.class);
        assertThat(errorCode).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getCode());
        Integer holdRows =
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transfer_hold WHERE customer_id = 1", Integer.class);
        assertThat(holdRows).isZero();
    }

    @Test
    @DisplayName("[메트릭] 보류는 transfer.count{result=HELD}로 센다")
    void holdPolicy_holds_recordsHeldMetric() {
        MeterRegistry registry = new SimpleMeterRegistry();
        TransferHoldPolicy holding = holdPolicy(1, context -> TransferHoldDecision.holdForApproval("TEST"));
        TransferExecutionService service =
                serviceWith(List.of(), List.of(), List.of(holding), transferHoldPort, eventPublisher, registry);

        service.execute(immediateCommand(30000L));

        assertThat(registry.get("transfer.count")
                        .tags("result", "HELD", "replayed", "false")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("[훅 C] 예약이체는 훅 C를 지나지 않는다")
    void holdPolicy_isNotCalledForScheduledTransfer() {
        List<TransferHoldContext> received = new ArrayList<>();
        TransferExecutionService service = serviceWithHoldPolicies(List.of(recordingHoldPolicy(1, received, null)));

        TransferResult result = service.execute(TransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(30000L)
                .transferType(TransferType.SCHEDULED)
                .channel(TransferChannel.BT)
                .sourceId(990771L)
                .executionDate(BUSINESS_DATE)
                .build());

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(received).isEmpty();
    }

    @Test
    @DisplayName("[훅 C] 같은 고객의 요청 둘이 겹치면 뒤 요청은 한도 락에서 기다렸다가, 훅에서 앞 요청이 커밋한 SUCCESS를 본다")
    void holdPolicy_secondRequestSeesFirstRequestsCommit() throws Exception {
        CountDownLatch firstInHook = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean isFirst = new AtomicBoolean(true);
        List<Integer> seenCounts = new CopyOnWriteArrayList<>();
        TransferHoldPolicy counting = holdPolicy(1, context -> {
            seenCounts.add(recentTransferQuery
                    .aggregateSucceeded(
                            context.customerId(),
                            context.depositAccountNumber(),
                            context.requestedAt().minusMinutes(10))
                    .count());
            if (isFirst.getAndSet(false)) {
                firstInHook.countDown();
                awaitQuietly(releaseFirst);
            }
            return TransferHoldDecision.proceed();
        });
        TransferExecutionService service = serviceWithHoldPolicies(List.of(counting));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<TransferResult> first = executor.submit(() -> service.execute(immediateCommand(30000L)));
            assertThat(firstInHook.await(10, TimeUnit.SECONDS)).isTrue();
            Future<TransferResult> second = executor.submit(() -> service.execute(immediateCommand(30000L)));
            // 직렬화가 깨졌다면 뒤 요청의 훅이 이 사이에 돌아 0건을 본다
            Thread.sleep(500);
            releaseFirst.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS).status()).isEqualTo(ProcessResultStatus.SUCCESS);
            assertThat(second.get(30, TimeUnit.SECONDS).status()).isEqualTo(ProcessResultStatus.SUCCESS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
        assertThat(seenCounts).containsExactly(0, 1);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static TransferHoldPolicy holdPolicy(
            int order, java.util.function.Function<TransferHoldContext, TransferHoldDecision> decide) {
        return new TransferHoldPolicy() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public TransferHoldDecision decide(TransferHoldContext context) {
                return decide.apply(context);
            }
        };
    }

    private static TransferHoldPolicy recordingHoldPolicy(
            int order, List<TransferHoldContext> received, TransferHoldDecision decision) {
        return holdPolicy(order, context -> {
            received.add(context);
            return decision == null ? TransferHoldDecision.proceed() : decision;
        });
    }

    private static TransferHoldPolicy orderRecordingHoldPolicy(int order, List<Integer> called) {
        return holdPolicy(order, context -> {
            called.add(order);
            return TransferHoldDecision.proceed();
        });
    }

    private static TransferCommand immediateCommandWithFlowId(long amount, String flowId) {
        return TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .otpAuthToken("dummy-otp-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(amount)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .flowId(flowId)
                .build();
    }

    private TransferExecutionService serviceWith(
            List<TransferPreCheck> preChecks, List<LedgerPostingHook> ledgerPostingHooks) {
        return serviceWith(preChecks, ledgerPostingHooks, new SimpleMeterRegistry());
    }

    private TransferExecutionService serviceWith(
            List<TransferPreCheck> preChecks, List<LedgerPostingHook> ledgerPostingHooks, MeterRegistry registry) {
        return serviceWith(preChecks, ledgerPostingHooks, List.of(), registry);
    }

    private TransferExecutionService serviceWithHoldPolicies(List<TransferHoldPolicy> holdPolicies) {
        return serviceWith(List.of(), List.of(), holdPolicies, new SimpleMeterRegistry());
    }

    private TransferExecutionService serviceWith(
            List<TransferPreCheck> preChecks,
            List<LedgerPostingHook> ledgerPostingHooks,
            List<TransferHoldPolicy> holdPolicies,
            MeterRegistry registry) {
        return serviceWith(preChecks, ledgerPostingHooks, holdPolicies, transferHoldPort, eventPublisher, registry);
    }

    private TransferExecutionService serviceWith(
            List<TransferPreCheck> preChecks,
            List<LedgerPostingHook> ledgerPostingHooks,
            List<TransferHoldPolicy> holdPolicies,
            TransferHoldPort holdPort,
            ApplicationEventPublisher publisher,
            MeterRegistry registry) {
        return new TransferExecutionService(
                accountLockPort,
                transferLimitPort,
                transferAuthTokenVerificationPort,
                transferOtpVerificationPort,
                transferSequencePort,
                transferSavePort,
                transferLookupPort,
                ledgerSavePort,
                holdPort,
                preChecks,
                ledgerPostingHooks,
                holdPolicies,
                clock,
                businessDateGate,
                transactionManager,
                publisher,
                registry);
    }

    private static TransferPreCheck recordingPreCheck(int order, List<Integer> called, boolean reject) {
        return new TransferPreCheck() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public void check(TransferPreCheckContext context) {
                called.add(order);
                if (reject) {
                    throw new BusinessException(TransferErrorCode.INSUFFICIENT_BALANCE);
                }
            }
        };
    }

    private static TransferCommand immediateCommand(long amount) {
        return TransferCommand.builder()
                .customerId(1L)
                .authToken("dummy-auth-token")
                .otpAuthToken("dummy-otp-token")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(amount)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .build();
    }

    private void assertRolledBack() {
        assertThat(balanceOf(101)).isEqualTo(100000L);
        assertThat(balanceOf(202)).isEqualTo(100000L);
        Integer ledgerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (101, 202)", Integer.class);
        assertThat(ledgerRows).isZero();
        Integer usageRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_limit_daily_usage WHERE customer_id = 1", Integer.class);
        assertThat(usageRows).isZero();
    }

    private List<String> transferStatuses() {
        return jdbcTemplate.queryForList(
                "SELECT status FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202",
                String.class);
    }

    private LocalDate transferTradeDate(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT trade_date FROM transfer WHERE transaction_number = ?", LocalDate.class, transactionNumber);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    // 보류 저장만 실패시키는 대역. 나머지는 실제 저장소로 넘긴다.
    private record FailingSaveTransferHoldPort(TransferHoldPort delegate, RuntimeException failure)
            implements TransferHoldPort {

        @Override
        public TransferHold save(TransferHold transferHold) {
            throw failure;
        }

        @Override
        public Optional<TransferHold> findById(long transferHoldId) {
            return delegate.findById(transferHoldId);
        }

        @Override
        public boolean markExecuting(long transferHoldId, Long actorId, LocalDateTime executingAt) {
            return delegate.markExecuting(transferHoldId, actorId, executingAt);
        }

        @Override
        public boolean finishExecution(
                long transferHoldId, TransferHoldStatus result, Long transferId, LocalDateTime completedAt) {
            return delegate.finishExecution(transferHoldId, result, transferId, completedAt);
        }

        @Override
        public boolean close(
                long transferHoldId,
                TransferHoldStatus result,
                Long actorId,
                String reasonCode,
                LocalDateTime completedAt) {
            return delegate.close(transferHoldId, result, actorId, reasonCode, completedAt);
        }
    }
}
