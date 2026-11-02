package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountStatusHistoryPersistenceAdapter;
import com.shinhan.corebank.account.domain.AccountType;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferExecutionJpaEntity;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferExecutionJpaRepository;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaEntity;
import com.shinhan.corebank.autotransfer.adapter.out.persistence.AutoTransferJpaRepository;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferRegisterCommand;
import com.shinhan.corebank.autotransfer.application.port.in.AutoTransferRegisterUseCase;
import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferPersistencePort;
import com.shinhan.corebank.autotransfer.application.port.out.DepositAccountInfo;
import com.shinhan.corebank.autotransfer.application.service.AutoTransferBatchItemProcessor;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.autotransfer.domain.exception.AutoTransferErrorCode;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import com.shinhan.corebank.scheduledtransfer.adapter.out.persistence.ScheduledTransferJpaEntity;
import com.shinhan.corebank.scheduledtransfer.adapter.out.persistence.ScheduledTransferJpaRepository;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterCommand;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterUseCase;
import com.shinhan.corebank.scheduledtransfer.application.service.ScheduledTransferBatchItemProcessor;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import com.shinhan.corebank.scheduledtransfer.domain.exception.ScheduledTransferErrorCode;
import com.shinhan.corebank.subscription.adapter.out.persistence.ProductSubscriptionJpaEntity;
import com.shinhan.corebank.subscription.adapter.out.persistence.ProductSubscriptionJpaRepository;
import com.shinhan.corebank.subscription.api.EarlyTerminationCommand;
import com.shinhan.corebank.subscription.api.EarlyTerminationResult;
import com.shinhan.corebank.subscription.api.EarlyTerminationUseCase;
import com.shinhan.corebank.subscription.application.port.out.MaturityTerminationPasswordVerificationPort;
import com.shinhan.corebank.subscription.domain.MaturityHandling;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

@DisplayName("PH-16/#844 중도해지 연결 이체 DB 통합 테스트")
class EarlyTerminationLinkedTransferIntegrationTest extends IntegrationTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final long PRODUCT_ACCOUNT_ID = 846_101L;
    private static final long WITHDRAWAL_ACCOUNT_ID = 846_102L;
    private static final long OTHER_ACCOUNT_ID = 846_103L;
    private static final long OPENING_LEDGER_ID = 846_100_000_001L;

    private static final String PRODUCT_NUMBER = "846000000101";
    private static final String WITHDRAWAL_NUMBER = "846000000102";
    private static final String OTHER_NUMBER = "846000000103";

    private static final long PRINCIPAL = 1_000_000L;
    private static final BigDecimal RATE = new BigDecimal("3.65");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2038, 9, 19);

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private EarlyTerminationUseCase terminationUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private BusinessDateTestFixture businessDateFixture;

    @Autowired
    private CustomerTestFixture customerFixture;

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private ProductSubscriptionJpaRepository subscriptionRepository;

    @Autowired
    private AutoTransferJpaRepository autoRepository;

    @Autowired
    private AutoTransferExecutionJpaRepository autoExecutionRepository;

    @Autowired
    private ScheduledTransferJpaRepository scheduledRepository;

    @Autowired
    private AutoTransferRegisterUseCase autoRegisterUseCase;

    @Autowired
    private ScheduledTransferRegisterUseCase scheduledRegisterUseCase;

    @Autowired
    private AutoTransferBatchItemProcessor autoBatchProcessor;

    @Autowired
    private AutoTransferPersistencePort autoPersistencePort;

    @Autowired
    private ScheduledTransferBatchItemProcessor scheduledBatchProcessor;

    @MockitoBean
    private com.shinhan.corebank.autotransfer.application.port.out.AccountStatusPort autoAccountStatusPort;

    @MockitoBean
    private com.shinhan.corebank.scheduledtransfer.application.port.out.AccountStatusPort scheduledAccountStatusPort;

    @MockitoBean
    private com.shinhan.corebank.autotransfer.application.port.out.AuthTokenVerificationPort autoPasswordPort;

    @MockitoBean
    private com.shinhan.corebank.scheduledtransfer.application.port.out.AuthTokenVerificationPort scheduledPasswordPort;

    @MockitoBean
    private com.shinhan.corebank.autotransfer.application.port.out.AutoTransferOtpVerificationPort autoOtpPort;

    @MockitoBean
    private com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferOtpVerificationPort
            scheduledOtpPort;

    @MockitoBean
    private com.shinhan.corebank.autotransfer.application.port.out.TransferLimitPort autoLimitPort;

    @MockitoBean
    private com.shinhan.corebank.scheduledtransfer.application.port.out.TransferLimitPort scheduledLimitPort;

    @MockitoBean
    private MaturityTerminationPasswordVerificationPort passwordVerificationPort;

    @MockitoSpyBean
    private AccountStatusHistoryPersistenceAdapter historyAdapter;

    private Long customerId;
    private Long productId;
    private LocalDate originalBusinessDate;
    private LocalDate openedDate;
    private LocalDate maturityDate;

    @BeforeEach
    void setUp() {
        openedDate = today().minusDays(120);
        maturityDate = today().plusDays(180);

        originalBusinessDate = businessDateFixture.currentInDb();
        businessDateFixture.moveTo(BUSINESS_DATE);

        customerId = customerFixture.createCustomer();
        productId = productRepository
                .saveAndFlush(ProductTestFixtures.productWithCode(
                        "PH844-" + UUID.randomUUID().toString().substring(0, 8)))
                .getProductId();

        seedAccounts();
        seedOpeningLedger();
        seedSubscription();
    }

    @AfterEach
    void tearDown() {
        if (customerId != null) {
            jdbcTemplate.update("DELETE FROM scheduled_transfer WHERE customer_id = ?", customerId);

            jdbcTemplate.update(
                    """
                    DELETE FROM auto_transfer_execution
                    WHERE auto_transfer_id IN (
                        SELECT auto_transfer_id
                        FROM auto_transfer
                        WHERE customer_id = ?
                    )
                    """,
                    customerId);

            jdbcTemplate.update("DELETE FROM auto_transfer WHERE customer_id = ?", customerId);
        }

        jdbcTemplate.update(
                """
                DELETE FROM gl_journal_entry
                WHERE voucher_no IN (
                    SELECT voucher_no FROM gl_voucher
                    WHERE trade_date = ?
                      AND tx_type IN ('INTEREST', 'TERMINATION')
                )
                """,
                BUSINESS_DATE);

        jdbcTemplate.update(
                """
                DELETE FROM gl_voucher
                WHERE trade_date = ?
                  AND tx_type IN ('INTEREST', 'TERMINATION')
                """,
                BUSINESS_DATE);

        jdbcTemplate.update(
                """
                DELETE FROM gl_voucher_sequence
                WHERE trade_date = ?
                  AND tx_type IN ('INTEREST', 'TERMINATION')
                """,
                BUSINESS_DATE);

        jdbcTemplate.update("DELETE FROM interest_payment WHERE account_id = ?", PRODUCT_ACCOUNT_ID);

        jdbcTemplate.update(
                "DELETE FROM ledger_entry WHERE account_id IN (?, ?, ?)",
                PRODUCT_ACCOUNT_ID,
                WITHDRAWAL_ACCOUNT_ID,
                OTHER_ACCOUNT_ID);

        jdbcTemplate.update("DELETE FROM account_status_history WHERE account_id = ?", PRODUCT_ACCOUNT_ID);

        jdbcTemplate.update("DELETE FROM product_subscription WHERE account_id = ?", PRODUCT_ACCOUNT_ID);

        jdbcTemplate.update(
                "DELETE FROM account WHERE account_id IN (?, ?, ?)",
                PRODUCT_ACCOUNT_ID,
                WITHDRAWAL_ACCOUNT_ID,
                OTHER_ACCOUNT_ID);

        if (productId != null) {
            productRepository.deleteById(productId);
        }

        if (customerId != null) {
            jdbcTemplate.update("DELETE FROM audit_log WHERE customer_id = ?", customerId);

            customerFixture.deleteCustomer(customerId);
        }

        if (originalBusinessDate != null) {
            businessDateFixture.moveTo(originalBusinessDate);
        }
    }

    @Test
    @DisplayName("출금·입금 양방향 연결 이체만 정리하고 무관한 이체는 유지한다")
    void terminate_cleansBothDirectionsButPreservesUnrelatedTransfers() {
        LocalDate future = today().plusDays(10);

        long outgoingAuto = seedAuto(PRODUCT_ACCOUNT_ID, WITHDRAWAL_NUMBER, future);
        long incomingAuto = seedAuto(WITHDRAWAL_ACCOUNT_ID, PRODUCT_NUMBER, future);
        long unrelatedAuto = seedAuto(WITHDRAWAL_ACCOUNT_ID, OTHER_NUMBER, future);

        long outgoingScheduled =
                seedScheduled(PRODUCT_ACCOUNT_ID, WITHDRAWAL_NUMBER, future, ScheduledTransferStatus.WAITING);
        long incomingScheduled =
                seedScheduled(WITHDRAWAL_ACCOUNT_ID, PRODUCT_NUMBER, future, ScheduledTransferStatus.WAITING);
        long unrelatedScheduled =
                seedScheduled(WITHDRAWAL_ACCOUNT_ID, OTHER_NUMBER, future, ScheduledTransferStatus.WAITING);

        EarlyTerminationResult result = terminationUseCase.terminate(command());

        assertThat(accountStatus()).isEqualTo("CLOSED");
        assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isZero();
        assertThat(balanceOf(WITHDRAWAL_ACCOUNT_ID)).isEqualTo(result.totalTransferred());

        assertThat(autoStatus(outgoingAuto)).isEqualTo("TERMINATED");
        assertThat(autoStatus(incomingAuto)).isEqualTo("TERMINATED");
        assertThat(autoStatus(unrelatedAuto)).isEqualTo("NORMAL");

        assertThat(scheduledStatus(outgoingScheduled)).isEqualTo("CANCELED");
        assertThat(scheduledStatus(incomingScheduled)).isEqualTo("CANCELED");
        assertThat(scheduledStatus(unrelatedScheduled)).isEqualTo("WAITING");

        assertThat(autoTerminatedAt(outgoingAuto)).isNotNull();
        assertThat(autoTerminatedAt(incomingAuto)).isNotNull();
        assertThat(scheduledCanceledAt(outgoingScheduled)).isNotNull();
        assertThat(scheduledCanceledAt(incomingScheduled)).isNotNull();

        assertThat(autoTerminatedAt(unrelatedAuto)).isNull();
        assertThat(scheduledCanceledAt(unrelatedScheduled)).isNull();

        assertThat(countTerminationLedger()).isEqualTo(2);
        assertThat(countVoucher("TERMINATION")).isEqualTo(1);
        assertThat(countClosureHistory()).isEqualTo(1);
    }

    @Test
    @DisplayName("자동이체 실행 예정일 당일이면 중도해지를 거부한다")
    void terminate_autoTransferDueTodayRejected() {
        long autoId = seedAuto(WITHDRAWAL_ACCOUNT_ID, PRODUCT_NUMBER, today());

        assertThatThrownBy(() -> terminationUseCase.terminate(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.CANNOT_TERMINATE_ON_EXECUTION_DATE));

        assertThat(autoStatus(autoId)).isEqualTo("NORMAL");
        assertNoTermination();
    }

    @Test
    @DisplayName("자동이체 회차가 PROCESSING이면 중도해지를 거부한다")
    void terminate_autoTransferProcessingRejected() {
        long autoId = seedAuto(WITHDRAWAL_ACCOUNT_ID, PRODUCT_NUMBER, today().plusDays(10));

        AutoTransferJpaEntity auto = autoRepository.findById(autoId).orElseThrow();

        autoExecutionRepository.saveAndFlush(AutoTransferExecutionJpaEntity.builder()
                .autoTransfer(auto)
                .executionDate(today().minusDays(1))
                .amount(10_000L)
                .status(ProcessResultStatus.PROCESSING)
                .executedAt(LocalDateTime.now(clock))
                .build());

        assertThatThrownBy(() -> terminationUseCase.terminate(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS));

        assertThat(autoStatus(autoId)).isEqualTo("NORMAL");
        assertNoTermination();
    }

    @Test
    @DisplayName("예약이체가 PROCESSING이면 중도해지를 거부한다")
    void terminate_scheduledProcessingRejected() {
        long scheduledId = seedScheduled(
                WITHDRAWAL_ACCOUNT_ID, PRODUCT_NUMBER, today().plusDays(10), ScheduledTransferStatus.PROCESSING);

        assertThatThrownBy(() -> terminationUseCase.terminate(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.NOT_IN_WAITING_STATUS));

        assertThat(scheduledStatus(scheduledId)).isEqualTo("PROCESSING");
        assertNoTermination();
    }

    @Test
    @DisplayName("예약이체 실행 예정일 당일이면 중도해지를 거부한다")
    void terminate_scheduledDueTodayRejected() {
        long scheduledId =
                seedScheduled(WITHDRAWAL_ACCOUNT_ID, PRODUCT_NUMBER, today(), ScheduledTransferStatus.WAITING);

        assertThatThrownBy(() -> terminationUseCase.terminate(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ScheduledTransferErrorCode.CANNOT_CANCEL_ON_EXECUTION_DATE));

        assertThat(scheduledStatus(scheduledId)).isEqualTo("WAITING");
        assertNoTermination();
    }

    @Test
    @DisplayName("종료 감사 이력 실패 시 연결 이체와 원리금 지급까지 롤백한다")
    void terminate_historyFailureRollsBackLinkedTransfers() {
        LocalDate future = today().plusDays(10);

        long autoId = seedAuto(WITHDRAWAL_ACCOUNT_ID, PRODUCT_NUMBER, future);

        long scheduledId =
                seedScheduled(WITHDRAWAL_ACCOUNT_ID, PRODUCT_NUMBER, future, ScheduledTransferStatus.WAITING);

        AccountStatusHistoryPersistenceAdapter targetSpy = AopTestUtils.getUltimateTargetObject(historyAdapter);

        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    throw new IllegalStateException("연결 이체 전체 롤백 확인");
                })
                .when(targetSpy)
                .recordEarlyClosure(
                        eq(PRODUCT_ACCOUNT_ID), eq(maturityDate), eq(BUSINESS_DATE), any(LocalDateTime.class));

        assertThatThrownBy(() -> terminationUseCase.terminate(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("연결 이체 전체 롤백 확인");

        assertThat(autoStatus(autoId)).isEqualTo("NORMAL");
        assertThat(scheduledStatus(scheduledId)).isEqualTo("WAITING");

        assertThat(autoTerminatedAt(autoId)).isNull();
        assertThat(scheduledCanceledAt(scheduledId)).isNull();

        assertNoTermination();
    }

    @Test
    @DisplayName("중도해지와 자동이체 등록이 경합해도 CLOSED 계좌에 NORMAL 자동이체가 남지 않는다")
    void terminate_racingWithAutoRegistration() throws Exception {
        prepareInstallmentPayee();
        stubAutoRegistration();

        AutoTransferRegisterCommand registration = AutoTransferRegisterCommand.builder()
                .customerId(customerId)
                .withdrawalAccountId(WITHDRAWAL_ACCOUNT_ID)
                .depositAccountNumber(PRODUCT_NUMBER)
                .payeeName("테스트")
                .amount(10_000L)
                .cycleMonths(1)
                .transferDay(15)
                .startDate(today().plusDays(5))
                .endDate(today().plusDays(90))
                .accountPasswordAuthToken("test-auto-password")
                .otpAuthToken("test-auto-otp")
                .requestIp("127.0.0.1")
                .build();

        CountDownLatch validated = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);

        // 등록 사전 검증이 모두 끝난 후, DB 저장 직전에 멈춘다.
        doAnswer(invocation -> {
                    validated.countDown();
                    if (!resume.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("자동이체 등록 대기 시간 초과");
                    }
                    return null;
                })
                .when(autoOtpPort)
                .verifyRegisterAndConsume(any(), any(), any(), any(), any(), any(), any(), any(), any());

        runRegistrationRace(
                () -> autoRegisterUseCase.register(registration),
                validated,
                resume,
                """
                SELECT COUNT(*)
                FROM auto_transfer
                WHERE deposit_account_number = ?
                  AND status = 'NORMAL'
                """);
    }

    @Test
    @DisplayName("중도해지와 예약이체 등록이 경합해도 CLOSED 계좌에 WAITING 예약이체가 남지 않는다")
    void terminate_racingWithScheduledRegistration() throws Exception {
        prepareInstallmentPayee();
        stubScheduledRegistration();

        ScheduledTransferRegisterCommand registration = ScheduledTransferRegisterCommand.builder()
                .customerId(customerId)
                .withdrawalAccountId(WITHDRAWAL_ACCOUNT_ID)
                .depositAccountNumber(PRODUCT_NUMBER)
                .payeeName("테스트")
                .amount(10_000L)
                .scheduledDate(today().plusDays(20))
                .accountPasswordAuthToken("test-scheduled-password")
                .otpAuthToken("test-scheduled-otp")
                .requestIp("127.0.0.1")
                .build();

        CountDownLatch validated = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);

        doAnswer(invocation -> {
                    validated.countDown();
                    if (!resume.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("예약이체 등록 대기 시간 초과");
                    }
                    return null;
                })
                .when(scheduledOtpPort)
                .verifyRegisterAndConsume(any(), any(), any(), any(), any(), any());

        runRegistrationRace(
                () -> scheduledRegisterUseCase.register(registration),
                validated,
                resume,
                """
                SELECT COUNT(*)
                FROM scheduled_transfer
                WHERE payee_account_number = ?
                  AND status = 'WAITING'
                """);
    }

    @Test
    @DisplayName("중도해지 이후 오래된 자동이체 스냅샷으로 회차를 선점할 수 없다")
    void terminatedAccount_staleAutoBatchSnapshotRejected() {
        long autoId = seedAuto(WITHDRAWAL_ACCOUNT_ID, PRODUCT_NUMBER, today().plusDays(10));

        // 배치가 먼저 읽어둔 NORMAL 스냅샷
        AutoTransfer staleSnapshot = autoPersistencePort.findById(autoId).orElseThrow();

        terminationUseCase.terminate(command());

        assertThat(accountStatus()).isEqualTo("CLOSED");
        assertThat(autoStatus(autoId)).isEqualTo("TERMINATED");

        assertThatThrownBy(() -> autoBatchProcessor.saveProcessing(staleSnapshot))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AutoTransferErrorCode.NOT_IN_NORMAL_STATUS));

        Long processingCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auto_transfer_execution
                WHERE auto_transfer_id = ?
                """,
                Long.class,
                autoId);

        assertThat(processingCount).isZero();
    }

    @Test
    @DisplayName("중도해지 이후 취소된 예약이체는 배치에서 선점할 수 없다")
    void terminatedAccount_scheduledBatchClaimRejected() {
        long scheduledId = seedScheduled(
                WITHDRAWAL_ACCOUNT_ID, PRODUCT_NUMBER, today().plusDays(10), ScheduledTransferStatus.WAITING);

        terminationUseCase.terminate(command());

        assertThat(accountStatus()).isEqualTo("CLOSED");
        assertThat(scheduledStatus(scheduledId)).isEqualTo("CANCELED");

        boolean claimed = scheduledBatchProcessor.claim(scheduledId);

        assertThat(claimed).isFalse();
        assertThat(scheduledStatus(scheduledId)).isEqualTo("CANCELED");
    }

    private void runRegistrationRace(
            Callable<?> registerAction, CountDownLatch validated, CountDownLatch resume, String activeTransferCountSql)
            throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // 스레드 A: 자동·예약이체 신규 등록.
            Future<?> registration = executor.submit(registerAction);

            // A가 사전 검증을 끝내고 저장 직전에 도착할 때까지 대기.
            assertThat(validated.await(10, TimeUnit.SECONDS))
                    .as("이체 등록이 사전 검증을 완료해야 한다")
                    .isTrue();

            // 스레드 B: 사전 검증 이후, 등록 저장 이전에 중도해지 시작.
            Future<?> termination = executor.submit(() -> terminationUseCase.terminate(command()));

            try {
                // 기존 구현에서는 이체가 아직 저장되지 않았으므로
                // 중도해지가 먼저 완료될 수 있다.
                //
                // 계좌 잠금으로 직렬화되는 구현이라면,
                // 등록 트랜잭션이 끝날 때까지 중도해지가 대기할 수 있다.
                termination.get(8, TimeUnit.SECONDS);
            } catch (TimeoutException expectedWhenSerialized) {
                // 잠금 대기 여부는 아래 최종 상태 검사로 판정한다.
            } finally {
                // 등록이 실제 저장 단계로 진행하도록 해제.
                resume.countDown();
            }

            try {
                registration.get(20, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                // 계좌가 먼저 CLOSED로 바뀌어 등록이 거절되는 것도
                // 직렬화된 올바른 결과다.
                if (!(exception.getCause() instanceof BusinessException)) {
                    throw exception;
                }
            }

            // 중도해지가 실패했다면 이 지점에서 테스트도 실패한다.
            termination.get(20, TimeUnit.SECONDS);

            assertThat(accountStatus()).isEqualTo("CLOSED");
            assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isZero();

            // 핵심 불변식:
            // CLOSED 계좌에 활성 자동·예약이체가 남아서는 안 된다.
            Long activeCount = jdbcTemplate.queryForObject(activeTransferCountSql, Long.class, PRODUCT_NUMBER);

            assertThat(activeCount).as("종료된 상품 계좌에 활성 연결 이체가 남아서는 안 된다").isZero();

        } finally {
            resume.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("동시성 테스트 스레드 종료")
                    .isTrue();
        }
    }

    private EarlyTerminationCommand command() {
        return new EarlyTerminationCommand(customerId, PRODUCT_ACCOUNT_ID, "test-account-password-token");
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    private long seedAuto(Long withdrawalAccountId, String depositAccountNumber, LocalDate nextExecutionDate) {

        LocalDateTime registeredAt = LocalDateTime.now(clock).minusDays(2);

        return autoRepository
                .saveAndFlush(AutoTransferJpaEntity.builder()
                        .customerId(customerId)
                        .withdrawalAccountId(withdrawalAccountId)
                        .depositAccountNumber(depositAccountNumber)
                        .payeeName("테스트")
                        .amount(10_000L)
                        .cycleMonths(1)
                        .transferDay(nextExecutionDate.getDayOfMonth())
                        .startDate(today().minusDays(30))
                        .endDate(today().plusYears(1))
                        .nextExecutionDate(nextExecutionDate)
                        .status(AutoTransferStatus.NORMAL)
                        .registeredAt(registeredAt)
                        .updatedAt(registeredAt)
                        .build())
                .getAutoTransferId();
    }

    private long seedScheduled(
            Long withdrawalAccountId,
            String payeeAccountNumber,
            LocalDate scheduledDate,
            ScheduledTransferStatus status) {

        return scheduledRepository
                .saveAndFlush(ScheduledTransferJpaEntity.builder()
                        .customerId(customerId)
                        .withdrawalAccountId(withdrawalAccountId)
                        .payeeBankCode("088")
                        .payeeAccountNumber(payeeAccountNumber)
                        .payeeName("테스트")
                        .amount(10_000L)
                        .scheduledDate(scheduledDate)
                        .status(status)
                        .registeredAt(LocalDateTime.now(clock).minusDays(2))
                        .build())
                .getScheduledTransferId();
    }

    private void assertNoTermination() {
        assertThat(accountStatus()).isEqualTo("ACTIVE");
        assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isEqualTo(PRINCIPAL);
        assertThat(balanceOf(WITHDRAWAL_ACCOUNT_ID)).isZero();

        assertThat(jdbcTemplate.queryForObject(
                        """
                SELECT COUNT(*) FROM interest_payment
                WHERE account_id = ?
                """,
                        Long.class,
                        PRODUCT_ACCOUNT_ID))
                .isZero();

        assertThat(jdbcTemplate.queryForObject(
                        """
                SELECT COUNT(*) FROM ledger_entry
                WHERE account_id = ?
                  AND transaction_type = 'INTEREST'
                """,
                        Long.class,
                        PRODUCT_ACCOUNT_ID))
                .isZero();

        assertThat(countTerminationLedger()).isZero();
        assertThat(countVoucher("INTEREST")).isZero();
        assertThat(countVoucher("TERMINATION")).isZero();
        assertThat(countClosureHistory()).isZero();
    }

    private String autoStatus(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM auto_transfer WHERE auto_transfer_id = ?", String.class, id);
    }

    private LocalDateTime autoTerminatedAt(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT terminated_at FROM auto_transfer WHERE auto_transfer_id = ?", LocalDateTime.class, id);
    }

    private String scheduledStatus(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM scheduled_transfer WHERE scheduled_transfer_id = ?", String.class, id);
    }

    private LocalDateTime scheduledCanceledAt(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT canceled_at FROM scheduled_transfer WHERE scheduled_transfer_id = ?", LocalDateTime.class, id);
    }

    private String accountStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM account WHERE account_id = ?", String.class, PRODUCT_ACCOUNT_ID);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private long countTerminationLedger() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM ledger_entry
                WHERE transaction_type = 'TERMINATION'
                  AND account_id IN (?, ?)
                """,
                Long.class,
                PRODUCT_ACCOUNT_ID,
                WITHDRAWAL_ACCOUNT_ID);
    }

    private long countVoucher(String type) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM gl_voucher
                WHERE trade_date = ? AND tx_type = ?
                """,
                Long.class,
                BUSINESS_DATE,
                type);
    }

    private long countClosureHistory() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM account_status_history
                WHERE account_id = ?
                  AND previous_status = 'ACTIVE'
                  AND new_status = 'CLOSED'
                """,
                Long.class,
                PRODUCT_ACCOUNT_ID);
    }

    private void seedAccounts() {
        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, account_number, customer_id, product_id,
                    account_type, balance, hold_amount, status,
                    password_hash, withdrawal_registered,
                    opened_date, maturity_date, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?,
                    'TIME_DEPOSIT', ?, 0, 'ACTIVE',
                    ?, FALSE, ?, ?, NOW(6), NOW(6)
                )
                """,
                PRODUCT_ACCOUNT_ID,
                PRODUCT_NUMBER,
                customerId,
                productId,
                PRINCIPAL,
                PASSWORD_HASH,
                openedDate.atTime(9, 0),
                maturityDate);

        seedDemandAccount(WITHDRAWAL_ACCOUNT_ID, WITHDRAWAL_NUMBER);
        seedDemandAccount(OTHER_ACCOUNT_ID, OTHER_NUMBER);
    }

    private void seedDemandAccount(long accountId, String accountNumber) {
        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, account_number, customer_id, product_id,
                    account_type, balance, hold_amount, status,
                    password_hash, withdrawal_registered,
                    withdrawal_registered_at, opened_date,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, NULL,
                    'DEMAND_DEPOSIT', 0, 0, 'ACTIVE',
                    ?, TRUE, NOW(6), ?, NOW(6), NOW(6)
                )
                """,
                accountId,
                accountNumber,
                customerId,
                PASSWORD_HASH,
                openedDate.minusDays(30).atTime(9, 0));
    }

    private void seedOpeningLedger() {
        String transactionNumber = openedDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "BT0000000846";

        jdbcTemplate.update(
                """
                INSERT INTO ledger_entry (
                    ledger_entry_id, account_id, transfer_id,
                    transaction_number, direction, amount,
                    balance_after, transaction_type,
                    transaction_content, channel,
                    reversed, reversal_id, occurred_at, trade_date
                ) VALUES (
                    ?, ?, NULL,
                    ?, 'DEPOSIT', ?,
                    ?, 'OPENING',
                    '개시', 'BT',
                    FALSE, NULL, ?, ?
                )
                """,
                OPENING_LEDGER_ID,
                PRODUCT_ACCOUNT_ID,
                transactionNumber,
                PRINCIPAL,
                PRINCIPAL,
                openedDate.atTime(9, 0),
                openedDate);
    }

    private void seedSubscription() {
        subscriptionRepository.saveAndFlush(ProductSubscriptionJpaEntity.builder()
                .customerId(customerId)
                .productId(productId)
                .accountId(PRODUCT_ACCOUNT_ID)
                .withdrawalAccountId(WITHDRAWAL_ACCOUNT_ID)
                .subscriptionAmount(PRINCIPAL)
                .termMonths((short) 10)
                .baseRate(RATE)
                .preferentialRate(BigDecimal.ZERO)
                .appliedRate(RATE)
                .maturityHandling(MaturityHandling.TRANSFER)
                .status(ProcessResultStatus.SUCCESS)
                .openedDate(openedDate)
                .maturityDate(maturityDate)
                .subscribedAt(openedDate.atTime(9, 0))
                .subscriptionTradeDate(openedDate)
                .build());
    }

    private void prepareInstallmentPayee() {
        jdbcTemplate.update(
                """
                UPDATE product
                SET product_group = 'SAVINGS',
                    deposit_type = 'INSTALLMENT',
                    product_name = '정기적금'
                WHERE product_id = ?
                """,
                productId);

        jdbcTemplate.update(
                """
                UPDATE account
                SET account_type = 'INSTALLMENT_SAVINGS'
                WHERE account_id = ?
                """,
                PRODUCT_ACCOUNT_ID);

        entityManager.clear();
    }

    private void stubAutoRegistration() {
        when(autoAccountStatusPort.belongsToCustomer(WITHDRAWAL_ACCOUNT_ID, customerId))
                .thenReturn(true);

        when(autoAccountStatusPort.isActiveAccount(WITHDRAWAL_ACCOUNT_ID)).thenReturn(true);

        when(autoAccountStatusPort.isWithdrawalRegistered(WITHDRAWAL_ACCOUNT_ID))
                .thenReturn(true);

        when(autoAccountStatusPort.findDepositAccountInfo(PRODUCT_NUMBER)).thenAnswer(invocation -> {
            if (!"ACTIVE".equals(accountStatus())) {
                return Optional.empty();
            }

            return Optional.of(new DepositAccountInfo(AccountType.INSTALLMENT_SAVINGS, maturityDate));
        });

        when(autoLimitPort.findOneTimeLimit(customerId)).thenReturn(1_000_000L);
    }

    private void stubScheduledRegistration() {
        when(scheduledAccountStatusPort.belongsToCustomer(WITHDRAWAL_ACCOUNT_ID, customerId))
                .thenReturn(true);

        when(scheduledAccountStatusPort.isActiveAccount(WITHDRAWAL_ACCOUNT_ID)).thenReturn(true);

        when(scheduledAccountStatusPort.isWithdrawalRegistered(WITHDRAWAL_ACCOUNT_ID))
                .thenReturn(true);

        when(scheduledAccountStatusPort.findAccountTypeByNumber(PRODUCT_NUMBER)).thenAnswer(invocation -> {
            if (!"ACTIVE".equals(accountStatus())) {
                return Optional.empty();
            }

            return Optional.of(AccountType.INSTALLMENT_SAVINGS);
        });

        when(scheduledLimitPort.findOneTimeLimit(customerId)).thenReturn(1_000_000L);
    }
}
