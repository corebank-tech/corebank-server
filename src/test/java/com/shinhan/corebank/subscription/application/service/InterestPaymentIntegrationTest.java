package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.gl.api.JournalRequest;
import com.shinhan.corebank.gl.application.service.JournalPostingService;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import com.shinhan.corebank.subscription.api.InterestPaymentCommand;
import com.shinhan.corebank.subscription.api.InterestPaymentReason;
import com.shinhan.corebank.subscription.api.InterestPaymentResult;
import com.shinhan.corebank.subscription.api.InterestPaymentUseCase;
import com.shinhan.corebank.subscription.domain.exception.SubscriptionErrorCode;
import com.shinhan.corebank.transfer.api.DepositChannel;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("PH-14 이자 지급 - 실제 원장/GL/이력 통합 테스트")
class InterestPaymentIntegrationTest extends IntegrationTestSupport {

    private static final long ACCOUNT_ID = 687_001L;
    private static final long OPENING_LEDGER_ID = 687_000_000_000L;
    private static final long PRINCIPAL = 100_000_000L;
    private static final LocalDate OPENED_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate END_EXCLUSIVE = LocalDate.of(2026, 9, 2);
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2038, 7, 19);
    private static final BigDecimal RATE = new BigDecimal("3.65");
    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private InterestPaymentUseCase interestPaymentUseCase;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BusinessDateTestFixture businessDateFixture;

    @Autowired
    private CustomerTestFixture customerFixture;

    @Autowired
    private ProductJpaRepository productRepository;

    // 실제 GL Bean을 감시한다. 실패 테스트에서만 예외를 주입하며 나머지 테스트는 실기표한다.
    @MockitoSpyBean
    private JournalPostingService postingService;

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
                        "PH687-" + UUID.randomUUID().toString().substring(0, 8)))
                .getProductId();

        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, account_number, customer_id, product_id,
                    account_type, balance, status, password_hash,
                    opened_date, maturity_date, created_at, updated_at
                ) VALUES (?, '687000000001', ?, ?, 'TIME_DEPOSIT', ?, 'MATURED', ?, ?, ?, NOW(6), NOW(6))
                """,
                ACCOUNT_ID,
                customerId,
                productId,
                PRINCIPAL,
                PASSWORD_HASH,
                OPENED_DATE,
                END_EXCLUSIVE);

        // 계산 시작일 직전 원장 잔액: PH-11은 현재 occurred_at 기준으로 적수를 조회한다.
        jdbcTemplate.update(
                """
                INSERT INTO ledger_entry (
                    ledger_entry_id, account_id, transfer_id, transaction_number,
                    direction, amount, balance_after, transaction_type,
                    transaction_content, channel, reversed, reversal_id, occurred_at
                ) VALUES (
                    ?, ?, NULL, '20260831BT0000000687',
                    'DEPOSIT', ?, ?, 'OPENING', '개시', 'BT', FALSE, NULL,
                    '2026-08-31 18:00:00'
                )
                """,
                OPENING_LEDGER_ID,
                ACCOUNT_ID,
                PRINCIPAL,
                PRINCIPAL);
    }

    @AfterEach
    void tearDown() {
        // FK: 분개 -> 전표. 원장과 지급 이력은 계좌별로 정리한다.
        jdbcTemplate.update(
                """
                DELETE FROM gl_journal_entry
                WHERE voucher_no IN (
                    SELECT voucher_no FROM gl_voucher
                    WHERE trade_date = ? AND tx_type = 'INTEREST'
                )
                """,
                BUSINESS_DATE);
        jdbcTemplate.update("DELETE FROM gl_voucher WHERE trade_date = ? AND tx_type = 'INTEREST'", BUSINESS_DATE);
        jdbcTemplate.update(
                "DELETE FROM gl_voucher_sequence WHERE trade_date = ? AND tx_type = 'INTEREST'", BUSINESS_DATE);
        jdbcTemplate.update("DELETE FROM interest_payment WHERE account_id = ?", ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id = ?", ACCOUNT_ID);
        jdbcTemplate.update("DELETE FROM account WHERE account_id = ?", ACCOUNT_ID);
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
    @DisplayName("실제 계산 -> 세후이자 원장 1행 -> GL 1전표/4분개 -> 지급 이력 1건")
    void pay_postsLedgerVoucherAndHistory() {
        InterestPaymentResult result = transactionTemplate.execute(status -> interestPaymentUseCase.pay(command(RATE)));

        assertThat(result).isNotNull();
        assertThat(result.gross()).isEqualTo(10_000L);
        assertThat(result.tax()).isEqualTo(1_540L);
        assertThat(result.net()).isEqualTo(8_460L);
        assertThat(result.tradeDate()).isEqualTo(BUSINESS_DATE);
        assertThat(balance()).isEqualTo(PRINCIPAL + 8_460L);

        List<Map<String, Object>> ledger = jdbcTemplate.queryForList(
                """
                SELECT transaction_number, trade_date, amount, direction, balance_after
                FROM ledger_entry WHERE account_id = ? AND transaction_type = 'INTEREST'
                """,
                ACCOUNT_ID);
        assertThat(ledger).singleElement().satisfies(row -> {
            assertThat(row.get("transaction_number")).isEqualTo(result.transactionNumber());
            assertThat(((java.sql.Date) row.get("trade_date")).toLocalDate()).isEqualTo(BUSINESS_DATE);
            assertThat(row.get("amount")).isEqualTo(8_460L);
            assertThat(row.get("direction")).isEqualTo("DEPOSIT");
            assertThat(row.get("balance_after")).isEqualTo(PRINCIPAL + 8_460L);
        });

        Map<String, Object> voucher = jdbcTemplate.queryForMap(
                "SELECT voucher_no, trade_date, tx_type FROM gl_voucher WHERE reference_key = ? AND tx_type = 'INTEREST'",
                result.transactionNumber());
        assertThat(((java.sql.Date) voucher.get("trade_date")).toLocalDate()).isEqualTo(BUSINESS_DATE);
        assertThat(voucher.get("tx_type")).isEqualTo("INTEREST");

        String voucherNo = (String) voucher.get("voucher_no");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT account_code, dr_cr, amount FROM gl_journal_entry WHERE voucher_no = ? ORDER BY line_no",
                        voucherNo))
                .extracting(row -> row.get("account_code"), row -> row.get("dr_cr"), row -> row.get("amount"))
                .containsExactly(
                        tuple("50100", "DEBIT", 10_000L),
                        tuple("20100", "CREDIT", 10_000L),
                        tuple("20100", "DEBIT", 1_540L),
                        tuple("20200", "CREDIT", 1_540L));

        Map<String, Object> history = jdbcTemplate.queryForMap(
                """
                SELECT payment_reason, gross_amount, tax_amount, net_amount, transaction_number, trade_date
                FROM interest_payment WHERE account_id = ?
                """,
                ACCOUNT_ID);
        assertThat(history.get("payment_reason")).isEqualTo("MATURITY");
        assertThat(history.get("gross_amount")).isEqualTo(10_000L);
        assertThat(history.get("tax_amount")).isEqualTo(1_540L);
        assertThat(history.get("net_amount")).isEqualTo(8_460L);
        assertThat(history.get("transaction_number")).isEqualTo(result.transactionNumber());
        assertThat(((java.sql.Date) history.get("trade_date")).toLocalDate()).isEqualTo(BUSINESS_DATE);
    }

    @Test
    @DisplayName("이자가 0원이면 원장/GL 없이 지급 이력만 저장한다")
    void pay_zeroInterestRecordsHistoryOnly() {
        InterestPaymentResult result =
                transactionTemplate.execute(status -> interestPaymentUseCase.pay(command(BigDecimal.ZERO)));

        assertThat(result).isNotNull();
        assertThat(result.gross()).isZero();
        assertThat(result.tax()).isZero();
        assertThat(result.net()).isZero();
        assertThat(result.transactionNumber()).isNull();
        assertThat(result.tradeDate()).isNull();
        assertThat(balance()).isEqualTo(PRINCIPAL);
        assertThat(interestLedgerCount()).isZero();
        assertThat(interestVoucherCount()).isZero();
        assertThat(historyCount()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT transaction_number, trade_date FROM interest_payment WHERE account_id = ?", ACCOUNT_ID))
                .containsEntry("transaction_number", null)
                .containsEntry("trade_date", null);
    }

    @Test
    @DisplayName("세금이 0원이면 GL은 2분개만 생성한다")
    void pay_zeroTaxCreatesTwoLines() {
        InterestPaymentResult result =
                transactionTemplate.execute(status -> interestPaymentUseCase.pay(command(new BigDecimal("0.01"))));

        assertThat(result).isNotNull();
        assertThat(result.gross()).isEqualTo(27L);
        assertThat(result.tax()).isZero();
        assertThat(result.net()).isEqualTo(27L);
        assertThat(balance()).isEqualTo(PRINCIPAL + 27L);
        assertThat(jdbcTemplate.queryForList(
                        """
                        SELECT j.account_code, j.dr_cr, j.amount
                        FROM gl_journal_entry j
                        JOIN gl_voucher v ON v.voucher_no = j.voucher_no
                        WHERE v.reference_key = ? AND v.tx_type = 'INTEREST'
                        ORDER BY j.line_no
                        """,
                        result.transactionNumber()))
                .extracting(row -> row.get("account_code"), row -> row.get("dr_cr"), row -> row.get("amount"))
                .containsExactly(tuple("50100", "DEBIT", 27L), tuple("20100", "CREDIT", 27L));
    }

    @Test
    @DisplayName("재요청하면 PRD0302로 거부하며 지급은 정확히 한 번만 남는다")
    void pay_duplicateRejected() {
        transactionTemplate.executeWithoutResult(status -> interestPaymentUseCase.pay(command(RATE)));

        assertThatThrownBy(() ->
                        transactionTemplate.executeWithoutResult(status -> interestPaymentUseCase.pay(command(RATE))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SubscriptionErrorCode.INTEREST_ALREADY_PAID);

        assertThat(balance()).isEqualTo(PRINCIPAL + 8_460L);
        assertThat(interestLedgerCount()).isEqualTo(1L);
        assertThat(interestVoucherCount()).isEqualTo(1L);
        assertThat(historyCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("후속 해지 처리 실패 시 원장/GL/이력/잔액이 함께 롤백된다")
    void pay_callerFailureRollsBackEverything() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    interestPaymentUseCase.pay(command(RATE));
                    throw new IllegalStateException("CLOSED 전이 실패");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CLOSED 전이 실패");

        assertNoPaymentCommitted();
    }

    @Test
    @DisplayName("GL 기표 실패 시 원장 입금도 롤백되고 지급 이력은 저장되지 않는다")
    void pay_glFailureRollsBackEverything() {
        // @MockitoSpyBean이 감싼 트랜잭션 프록시를 우회해 실제 spy에만 실패를 설정한다.
        // 프록시에 직접 스텁을 설정하면 MANDATORY 메서드가 트랜잭션 없이 호출되어 실패한다.
        JournalPostingService targetSpy = AopTestUtils.getUltimateTargetObject(postingService);
        doThrow(new IllegalStateException("GL 실패 주입")).when(targetSpy).post(any(JournalRequest.class));

        assertThatThrownBy(() ->
                        transactionTemplate.executeWithoutResult(status -> interestPaymentUseCase.pay(command(RATE))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("GL 실패 주입");

        assertNoPaymentCommitted();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 지급을 호출하면 MANDATORY 계약으로 거부한다")
    void pay_requiresCallerTransaction() {
        assertThatThrownBy(() -> interestPaymentUseCase.pay(command(RATE)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertNoPaymentCommitted();
    }

    @Test
    @DisplayName("동시 요청 두 건 중 하나만 지급되고 계좌 잔액과 장부에 한 건만 남는다")
    void pay_concurrentRequestsDoNotDoublePay() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Attempt> attempt = () -> {
            start.await();
            try {
                transactionTemplate.executeWithoutResult(status -> interestPaymentUseCase.pay(command(RATE)));
                return new Attempt(true, null);
            } catch (RuntimeException e) {
                return new Attempt(false, e);
            }
        };

        try {
            Future<Attempt> first = executor.submit(attempt);
            Future<Attempt> second = executor.submit(attempt);
            start.countDown();

            Attempt a = first.get(30, TimeUnit.SECONDS);
            Attempt b = second.get(30, TimeUnit.SECONDS);
            assertThat(List.of(a.success(), b.success())).containsExactlyInAnyOrder(true, false);
            Throwable rejected = a.success() ? b.error() : a.error();
            assertThat(rejected).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) rejected).getErrorCode())
                    .isEqualTo(SubscriptionErrorCode.INTEREST_ALREADY_PAID);

            assertThat(balance()).isEqualTo(PRINCIPAL + 8_460L);
            assertThat(interestLedgerCount()).isEqualTo(1L);
            assertThat(interestVoucherCount()).isEqualTo(1L);
            assertThat(historyCount()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private record Attempt(boolean success, Throwable error) {}

    private static InterestPaymentCommand command(BigDecimal rate) {
        return new InterestPaymentCommand(
                ACCOUNT_ID,
                OPENED_DATE,
                END_EXCLUSIVE,
                rate,
                InterestPaymentCommand.CalculationMode.SIMPLE,
                DepositChannel.BT,
                InterestPaymentReason.MATURITY);
    }

    private void assertNoPaymentCommitted() {
        assertThat(balance()).isEqualTo(PRINCIPAL);
        assertThat(interestLedgerCount()).isZero();
        assertThat(interestVoucherCount()).isZero();
        assertThat(historyCount()).isZero();
    }

    private long balance() {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, ACCOUNT_ID);
    }

    private long interestLedgerCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id = ? AND transaction_type = 'INTEREST'",
                Long.class,
                ACCOUNT_ID);
    }

    private long interestVoucherCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM gl_voucher WHERE trade_date = ? AND tx_type = 'INTEREST'",
                Long.class,
                BUSINESS_DATE);
    }

    private long historyCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interest_payment WHERE account_id = ?", Long.class, ACCOUNT_ID);
    }
}
