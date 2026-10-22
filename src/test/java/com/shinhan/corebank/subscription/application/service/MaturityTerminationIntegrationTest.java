package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountStatusHistoryPersistenceAdapter;
import com.shinhan.corebank.account.application.service.MaturityAccountSettlementService;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import com.shinhan.corebank.subscription.adapter.out.persistence.InterestPaymentPersistenceAdapter;
import com.shinhan.corebank.subscription.adapter.out.persistence.ProductSubscriptionJpaEntity;
import com.shinhan.corebank.subscription.adapter.out.persistence.ProductSubscriptionJpaRepository;
import com.shinhan.corebank.subscription.api.MaturityTerminationCommand;
import com.shinhan.corebank.subscription.api.MaturityTerminationResult;
import com.shinhan.corebank.subscription.api.MaturityTerminationUseCase;
import com.shinhan.corebank.subscription.domain.MaturityHandling;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

@DisplayName("PH-15 만기해지 실제 DB 통합 테스트")
class MaturityTerminationIntegrationTest extends IntegrationTestSupport {

    private static final long PRODUCT_ACCOUNT_ID = 685_001L;
    private static final long WITHDRAWAL_ACCOUNT_ID = 685_002L;
    private static final long OPENING_LEDGER_ID = 685_000_000_001L;

    private static final long PRINCIPAL = 1_000_000L;

    private static final LocalDate OPENED_DATE = LocalDate.of(2026, 3, 1);
    private static final LocalDate MATURITY_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2038, 7, 20);

    private static final BigDecimal RATE = new BigDecimal("3.65");

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private MaturityTerminationUseCase terminationUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BusinessDateTestFixture businessDateFixture;

    @Autowired
    private CustomerTestFixture customerFixture;

    @Autowired
    private ProductJpaRepository productRepository;

    @Autowired
    private ProductSubscriptionJpaRepository subscriptionRepository;

    // 정상 처리에서는 실제 메서드를 실행한다.
    // 롤백 테스트에서만 closeMatured 실패를 주입한다.
    @MockitoSpyBean
    private MaturityAccountSettlementService settlementService;

    // 실제 DB 조회를 수행하면서 두 요청의 경합 시점만 맞춘다.
    @MockitoSpyBean
    private InterestPaymentPersistenceAdapter interestPaymentPersistenceAdapter;

    @MockitoSpyBean
    private AccountStatusHistoryPersistenceAdapter historyAdapter;

    private Long customerId;
    private Long productId;
    private LocalDate originalBusinessDate;

    @BeforeEach
    void setUp() {
        originalBusinessDate = businessDateFixture.currentInDb();
        businessDateFixture.moveTo(BUSINESS_DATE);

        customerId = customerFixture.createCustomer();

        productId = productRepository
                .saveAndFlush(ProductTestFixtures.productWithCode(
                        "PH685-" + UUID.randomUUID().toString().substring(0, 8)))
                .getProductId();

        seedAccounts();
        seedOpeningLedger();
        seedSubscription();
    }

    @AfterEach
    void tearDown() {
        // GL 분개를 전표보다 먼저 삭제한다.
        jdbcTemplate.update(
                """
                DELETE FROM gl_journal_entry
                WHERE voucher_no IN (
                    SELECT voucher_no
                    FROM gl_voucher
                    WHERE trade_date = ?
                      AND tx_type = 'INTEREST'
                )
                """,
                BUSINESS_DATE);

        jdbcTemplate.update(
                """
                DELETE FROM gl_voucher
                WHERE trade_date = ? AND tx_type = 'INTEREST'
                """,
                BUSINESS_DATE);

        jdbcTemplate.update(
                """
                DELETE FROM gl_voucher_sequence
                WHERE trade_date = ? AND tx_type = 'INTEREST'
                """,
                BUSINESS_DATE);

        jdbcTemplate.update("DELETE FROM interest_payment WHERE account_id = ?", PRODUCT_ACCOUNT_ID);

        jdbcTemplate.update(
                "DELETE FROM ledger_entry WHERE account_id IN (?, ?)", PRODUCT_ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID);

        jdbcTemplate.update("DELETE FROM account_status_history WHERE account_id = ?", PRODUCT_ACCOUNT_ID);

        jdbcTemplate.update("DELETE FROM product_subscription WHERE account_id = ?", PRODUCT_ACCOUNT_ID);

        jdbcTemplate.update(
                "DELETE FROM account WHERE account_id IN (?, ?)", PRODUCT_ACCOUNT_ID, WITHDRAWAL_ACCOUNT_ID);

        if (productId != null) {
            productRepository.deleteById(productId);
        }

        if (customerId != null) {
            customerFixture.deleteCustomer(customerId);
        }

        if (originalBusinessDate != null) {
            businessDateFixture.moveTo(originalBusinessDate);
        }
    }

    @Test
    @DisplayName("만기해지 성공 시 이자·원리금 원장과 계좌 CLOSED가 함께 저장된다")
    void terminate_success() {
        MaturityTerminationResult result = terminationUseCase.terminate(command());

        long expectedTransferred = PRINCIPAL + result.netInterest();

        assertThat(result.principal()).isEqualTo(PRINCIPAL);
        assertThat(result.netInterest()).isPositive();
        assertThat(result.totalTransferred()).isEqualTo(expectedTransferred);
        assertThat(result.tradeDate()).isEqualTo(BUSINESS_DATE);

        // 계좌 상태 및 잔액 확인
        Map<String, Object> account = jdbcTemplate.queryForMap(
                """
                SELECT balance, status, closed_date, closed_trade_date
                FROM account
                WHERE account_id = ?
                """,
                PRODUCT_ACCOUNT_ID);

        assertThat(account.get("balance")).isEqualTo(0L);
        assertThat(account.get("status")).isEqualTo("CLOSED");
        assertThat(account.get("closed_date")).isNotNull();
        assertThat(((java.sql.Date) account.get("closed_trade_date")).toLocalDate())
                .isEqualTo(BUSINESS_DATE);

        assertThat(balanceOf(WITHDRAWAL_ACCOUNT_ID)).isEqualTo(expectedTransferred);

        // MATURITY 원장은 상품계좌 출금 1행 + 수취계좌 입금 1행
        List<Map<String, Object>> maturityLedger = jdbcTemplate.queryForList(
                """
                        SELECT account_id, direction, amount,
                               balance_after, transaction_number, trade_date
                        FROM ledger_entry
                        WHERE transaction_type = 'MATURITY'
                          AND account_id IN (?, ?)
                        """,
                PRODUCT_ACCOUNT_ID,
                WITHDRAWAL_ACCOUNT_ID);

        assertThat(maturityLedger).hasSize(2);

        Map<String, Object> withdrawal = maturityLedger.stream()
                .filter(row -> "WITHDRAWAL".equals(row.get("direction")))
                .findFirst()
                .orElseThrow();

        Map<String, Object> deposit = maturityLedger.stream()
                .filter(row -> "DEPOSIT".equals(row.get("direction")))
                .findFirst()
                .orElseThrow();

        assertThat(withdrawal.get("account_id")).isEqualTo(PRODUCT_ACCOUNT_ID);
        assertThat(withdrawal.get("amount")).isEqualTo(expectedTransferred);
        assertThat(withdrawal.get("balance_after")).isEqualTo(0L);

        assertThat(deposit.get("account_id")).isEqualTo(WITHDRAWAL_ACCOUNT_ID);
        assertThat(deposit.get("amount")).isEqualTo(expectedTransferred);

        assertThat(withdrawal.get("transaction_number")).isEqualTo(result.transactionNumber());
        assertThat(deposit.get("transaction_number")).isEqualTo(result.transactionNumber());

        // closed_trade_date와 MATURITY 출금 원장의 거래일이 같다.
        assertThat(((java.sql.Date) withdrawal.get("trade_date")).toLocalDate()).isEqualTo(BUSINESS_DATE);

        // 이자 지급 원장 / 이력 / GL 확인
        assertThat(countInterestLedger()).isEqualTo(1L);
        assertThat(countMaturityLedger()).isEqualTo(2L);
        assertThat(countInterestHistory()).isEqualTo(1L);
        assertThat(countInterestVoucher()).isEqualTo(1L);

        // 원장 합계와 실제 계좌 잔액 정합성
        assertThat(ledgerSumOf(PRODUCT_ACCOUNT_ID)).isZero();
        assertThat(ledgerSumOf(WITHDRAWAL_ACCOUNT_ID)).isEqualTo(expectedTransferred);
        // CLOSED 감사 이력이 정확히 한 건 저장된다.
        assertThat(countClosureHistory()).isEqualTo(1L);

        Map<String, Object> history = jdbcTemplate.queryForMap(
                """
        SELECT previous_status,
               new_status,
               maturity_date,
               effective_business_date,
               changed_at,
               trigger_type
        FROM account_status_history
        WHERE account_id = ?
          AND previous_status = 'MATURED'
          AND new_status = 'CLOSED'
        """,
                PRODUCT_ACCOUNT_ID);

        assertThat(history.get("previous_status")).isEqualTo("MATURED");

        assertThat(history.get("new_status")).isEqualTo("CLOSED");

        assertThat(((java.sql.Date) history.get("maturity_date")).toLocalDate()).isEqualTo(MATURITY_DATE);

        assertThat(((java.sql.Date) history.get("effective_business_date")).toLocalDate())
                .isEqualTo(result.tradeDate());

        assertThat((LocalDateTime) history.get("changed_at"))
                .isCloseTo(result.closedAt(), within(1, ChronoUnit.MICROS));

        assertThat(history.get("trigger_type")).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("이미 해지한 계좌를 재요청해도 원리금이 다시 지급되지 않는다")
    void terminate_duplicateRejected() {
        terminationUseCase.terminate(command());

        assertThatThrownBy(() -> terminationUseCase.terminate(command()))
                .isInstanceOf(com.shinhan.corebank.common.exception.BusinessException.class);

        assertThat(countInterestLedger()).isEqualTo(1L);
        assertThat(countMaturityLedger()).isEqualTo(2L);
        assertThat(countInterestHistory()).isEqualTo(1L);
        assertThat(countInterestVoucher()).isEqualTo(1L);

        assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isZero();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM account WHERE account_id = ?", String.class, PRODUCT_ACCOUNT_ID))
                .isEqualTo("CLOSED");

        assertThat(countClosureHistory()).isEqualTo(1L);
    }

    @Test
    @DisplayName("계좌 종료 단계 실패 시 이자·원장·GL·잔액이 모두 롤백된다")
    void terminate_closeFailureRollsBack() {
        // PH-14 통합 테스트와 동일하게 트랜잭션 프록시 내부의
        // 실제 spy 객체에만 예외를 주입한다.
        MaturityAccountSettlementService targetSpy = AopTestUtils.getUltimateTargetObject(settlementService);

        doThrow(new IllegalStateException("CLOSED 전이 실패 주입"))
                .when(targetSpy)
                .closeMatured(eq(PRODUCT_ACCOUNT_ID), eq(customerId), any(LocalDateTime.class), eq(BUSINESS_DATE));

        assertThatThrownBy(() -> terminationUseCase.terminate(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CLOSED 전이 실패 주입");

        // 최초 잔액과 상태가 유지돼야 한다.
        assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isEqualTo(PRINCIPAL);
        assertThat(balanceOf(WITHDRAWAL_ACCOUNT_ID)).isZero();

        Map<String, Object> account = jdbcTemplate.queryForMap(
                """
                SELECT status, closed_date, closed_trade_date
                FROM account
                WHERE account_id = ?
                """,
                PRODUCT_ACCOUNT_ID);

        assertThat(account.get("status")).isEqualTo("MATURED");
        assertThat(account.get("closed_date")).isNull();
        assertThat(account.get("closed_trade_date")).isNull();

        // 지급 관련 데이터가 모두 롤백돼야 한다.
        assertThat(countInterestLedger()).isZero();
        assertThat(countMaturityLedger()).isZero();
        assertThat(countInterestHistory()).isZero();
        assertThat(countInterestVoucher()).isZero();

        // 기존 OPENING 원장만 남는다.
        assertThat(ledgerSumOf(PRODUCT_ACCOUNT_ID)).isEqualTo(PRINCIPAL);

        assertThat(countClosureHistory()).isZero();
    }

    @Test
    @DisplayName("동시 만기해지 두 건 중 한 건만 성공하고 원리금은 한 번만 지급된다")
    void terminate_concurrentRequestsOnlyOnePayout() throws Exception {

        // 두 요청 모두 '이자 미지급' 조회를 마칠 때까지 대기한다.
        CountDownLatch bothObservedUnpaid = new CountDownLatch(2);

        InterestPaymentPersistenceAdapter targetSpy =
                AopTestUtils.getUltimateTargetObject(interestPaymentPersistenceAdapter);

        doAnswer(invocation -> {
                    // 실제 DB에서 지급 이력 존재 여부를 조회한다.
                    boolean alreadyPaid = (boolean) invocation.callRealMethod();

                    if (!alreadyPaid) {
                        bothObservedUnpaid.countDown();

                        if (!bothObservedUnpaid.await(20, TimeUnit.SECONDS)) {
                            throw new AssertionError("두 요청이 모두 이자 미지급 검증에 도달하지 못했습니다.");
                        }
                    }

                    return alreadyPaid;
                })
                .when(targetSpy)
                .existsByAccountId(PRODUCT_ACCOUNT_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<TerminationAttempt> attempt = () -> {
            start.await();

            try {
                MaturityTerminationResult result = terminationUseCase.terminate(command());

                return new TerminationAttempt(result, null);

            } catch (RuntimeException exception) {
                return new TerminationAttempt(null, exception);
            }
        };

        try {
            Future<TerminationAttempt> first = executor.submit(attempt);

            Future<TerminationAttempt> second = executor.submit(attempt);

            // 두 요청을 동시에 시작한다.
            start.countDown();

            TerminationAttempt a = first.get(40, TimeUnit.SECONDS);

            TerminationAttempt b = second.get(40, TimeUnit.SECONDS);

            List<TerminationAttempt> attempts = List.of(a, b);

            // 두 요청 모두 실제 미지급 조회를 통과했는지 확인
            assertThat(bothObservedUnpaid.getCount()).isZero();

            // 정확히 하나만 성공해야 한다.
            long successCount =
                    attempts.stream().filter(item -> item.result() != null).count();

            long failureCount =
                    attempts.stream().filter(item -> item.error() != null).count();

            assertThat(successCount).isEqualTo(1L);
            assertThat(failureCount).isEqualTo(1L);

            MaturityTerminationResult succeeded = attempts.stream()
                    .filter(item -> item.result() != null)
                    .findFirst()
                    .orElseThrow()
                    .result();

            RuntimeException rejected = attempts.stream()
                    .filter(item -> item.error() != null)
                    .findFirst()
                    .orElseThrow()
                    .error();

            assertThat(rejected).isNotNull();

            // 이자와 원금이 실제로 한 번만 지급되었는지 확인
            assertThat(succeeded.principal()).isEqualTo(PRINCIPAL);

            assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isZero();

            assertThat(balanceOf(WITHDRAWAL_ACCOUNT_ID)).isEqualTo(succeeded.totalTransferred());

            // 이자 지급 원장은 한 건이어야 한다.
            assertThat(countInterestLedger()).isEqualTo(1L);

            // MATURITY 출금/입금 원장은 정확히 두 행
            assertThat(countMaturityLedger()).isEqualTo(2L);

            // 이자 지급 이력도 정확히 한 건
            assertThat(countInterestHistory()).isEqualTo(1L);

            // 이자 GL 전표도 정확히 한 건
            assertThat(countInterestVoucher()).isEqualTo(1L);

            // 원장 합계와 계좌 잔액 정합성
            assertThat(ledgerSumOf(PRODUCT_ACCOUNT_ID)).isZero();

            assertThat(ledgerSumOf(WITHDRAWAL_ACCOUNT_ID)).isEqualTo(succeeded.totalTransferred());

            // 계좌는 CLOSED로 한 번만 종료된 상태여야 한다.
            Map<String, Object> account = jdbcTemplate.queryForMap(
                    """
                SELECT status, closed_date, closed_trade_date
                FROM account
                WHERE account_id = ?
                """,
                    PRODUCT_ACCOUNT_ID);

            assertThat(account.get("status")).isEqualTo("CLOSED");
            assertThat(account.get("closed_date")).isNotNull();

            assertThat(((java.sql.Date) account.get("closed_trade_date")).toLocalDate())
                    .isEqualTo(succeeded.tradeDate());

            // 동시 요청 두 건 중 실제 CLOSED 전이는 한 번만 기록되어야 한다.
            assertThat(countClosureHistory()).isEqualTo(1L);

        } finally {
            start.countDown();
            executor.shutdownNow();

            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    @DisplayName("RENEW 가입 건은 이자 지급과 원리금 이체 없이 거부한다")
    void terminate_renewRejected() {

        jdbcTemplate.update(
                """
            UPDATE product_subscription
            SET maturity_handling = 'RENEW'
            WHERE account_id = ?
            """,
                PRODUCT_ACCOUNT_ID);

        assertThatThrownBy(() -> terminationUseCase.terminate(command()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SubscriptionErrorCode.MATURITY_RENEW_UNSUPPORTED);

        assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isEqualTo(PRINCIPAL);

        assertThat(balanceOf(WITHDRAWAL_ACCOUNT_ID)).isZero();

        assertThat(countInterestLedger()).isZero();
        assertThat(countMaturityLedger()).isZero();
        assertThat(countInterestHistory()).isZero();
        assertThat(countInterestVoucher()).isZero();
        assertThat(countClosureHistory()).isZero();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM account WHERE account_id = ?", String.class, PRODUCT_ACCOUNT_ID))
                .isEqualTo("MATURED");
    }

    @Test
    @DisplayName("CLOSED 감사 이력 저장 실패 시 원리금 지급까지 전체 롤백한다")
    void terminate_historyFailureRollsBack() {

        AccountStatusHistoryPersistenceAdapter targetSpy = AopTestUtils.getUltimateTargetObject(historyAdapter);

        doAnswer(invocation -> {
                    // 실제 INSERT를 실행한 다음 실패시킨다.
                    invocation.callRealMethod();

                    throw new IllegalStateException("CLOSED 감사 이력 실패 주입");
                })
                .when(targetSpy)
                .recordMaturityClosure(
                        eq(PRODUCT_ACCOUNT_ID), eq(MATURITY_DATE), eq(BUSINESS_DATE), any(LocalDateTime.class));

        assertThatThrownBy(() -> terminationUseCase.terminate(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CLOSED 감사 이력 실패 주입");

        // 잔액 롤백
        assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isEqualTo(PRINCIPAL);

        assertThat(balanceOf(WITHDRAWAL_ACCOUNT_ID)).isZero();

        // 원장·GL·이자 지급 이력 롤백
        assertThat(countInterestLedger()).isZero();
        assertThat(countMaturityLedger()).isZero();
        assertThat(countInterestHistory()).isZero();
        assertThat(countInterestVoucher()).isZero();

        // 감사 이력도 롤백
        assertThat(countClosureHistory()).isZero();

        // 계좌 종료도 롤백
        Map<String, Object> account = jdbcTemplate.queryForMap(
                """
            SELECT status, closed_date, closed_trade_date
            FROM account
            WHERE account_id = ?
            """,
                PRODUCT_ACCOUNT_ID);

        assertThat(account.get("status")).isEqualTo("MATURED");
        assertThat(account.get("closed_date")).isNull();
        assertThat(account.get("closed_trade_date")).isNull();
    }

    private MaturityTerminationCommand command() {
        return new MaturityTerminationCommand(customerId, PRODUCT_ACCOUNT_ID);
    }

    private record TerminationAttempt(MaturityTerminationResult result, RuntimeException error) {}

    private void seedAccounts() {
        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, account_number, customer_id, product_id,
                    account_type, balance, hold_amount, status,
                    password_hash, withdrawal_registered,
                    opened_date, maturity_date, created_at, updated_at
                ) VALUES (
                    ?, '685000000001', ?, ?,
                    'TIME_DEPOSIT', ?, 0, 'MATURED',
                    ?, FALSE, ?, ?, NOW(6), NOW(6)
                )
                """,
                PRODUCT_ACCOUNT_ID,
                customerId,
                productId,
                PRINCIPAL,
                PASSWORD_HASH,
                OPENED_DATE.atTime(9, 0),
                MATURITY_DATE);

        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, account_number, customer_id, product_id,
                    account_type, balance, hold_amount, status,
                    password_hash, withdrawal_registered,
                    withdrawal_registered_at, opened_date,
                    created_at, updated_at
                ) VALUES (
                    ?, '685000000002', ?, NULL,
                    'DEMAND_DEPOSIT', 0, 0, 'ACTIVE',
                    ?, TRUE, NOW(6), '2026-02-01 09:00:00',
                    NOW(6), NOW(6)
                )
                """,
                WITHDRAWAL_ACCOUNT_ID,
                customerId,
                PASSWORD_HASH);
    }

    private void seedOpeningLedger() {
        jdbcTemplate.update(
                """
                INSERT INTO ledger_entry (
                    ledger_entry_id, account_id, transfer_id,
                    transaction_number, direction, amount,
                    balance_after, transaction_type,
                    transaction_content, channel,
                    reversed, reversal_id, occurred_at
                ) VALUES (
                    ?, ?, NULL,
                    '20260228BT0000000685', 'DEPOSIT', ?,
                    ?, 'OPENING',
                    '개시', 'BT',
                    FALSE, NULL, '2026-02-28 18:00:00'
                )
                """,
                OPENING_LEDGER_ID,
                PRODUCT_ACCOUNT_ID,
                PRINCIPAL,
                PRINCIPAL);
    }

    private void seedSubscription() {
        subscriptionRepository.saveAndFlush(ProductSubscriptionJpaEntity.builder()
                .customerId(customerId)
                .productId(productId)
                .accountId(PRODUCT_ACCOUNT_ID)
                .withdrawalAccountId(WITHDRAWAL_ACCOUNT_ID)
                .subscriptionAmount(PRINCIPAL)
                .termMonths((short) 6)
                .baseRate(RATE)
                .preferentialRate(BigDecimal.ZERO)
                .appliedRate(RATE)
                .maturityHandling(MaturityHandling.TRANSFER)
                .status(ProcessResultStatus.SUCCESS)
                .openedDate(OPENED_DATE)
                .maturityDate(MATURITY_DATE)
                .subscribedAt(OPENED_DATE.atTime(9, 0))
                .subscriptionTradeDate(OPENED_DATE)
                .build());
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private long countInterestLedger() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM ledger_entry
                WHERE account_id = ? AND transaction_type = 'INTEREST'
                """,
                Long.class,
                PRODUCT_ACCOUNT_ID);
    }

    private long countMaturityLedger() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM ledger_entry
                WHERE transaction_type = 'MATURITY'
                  AND account_id IN (?, ?)
                """,
                Long.class,
                PRODUCT_ACCOUNT_ID,
                WITHDRAWAL_ACCOUNT_ID);
    }

    private long countInterestHistory() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interest_payment WHERE account_id = ?", Long.class, PRODUCT_ACCOUNT_ID);
    }

    private long countInterestVoucher() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM gl_voucher
                WHERE trade_date = ? AND tx_type = 'INTEREST'
                """,
                Long.class,
                BUSINESS_DATE);
    }

    private long ledgerSumOf(long accountId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(
                    CASE direction
                        WHEN 'DEPOSIT' THEN amount
                        ELSE -amount
                    END
                ), 0)
                FROM ledger_entry
                WHERE account_id = ?
                """,
                Long.class,
                accountId);
    }

    private long countClosureHistory() {
        return jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*)
            FROM account_status_history
            WHERE account_id = ?
              AND previous_status = 'MATURED'
              AND new_status = 'CLOSED'
            """,
                Long.class,
                PRODUCT_ACCOUNT_ID);
    }
}
