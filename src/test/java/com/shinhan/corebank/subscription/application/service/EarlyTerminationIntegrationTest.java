package com.shinhan.corebank.subscription.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.adapter.out.persistence.AccountStatusHistoryPersistenceAdapter;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.product.adapter.out.persistence.ProductJpaRepository;
import com.shinhan.corebank.product.adapter.out.persistence.ProductTestFixtures;
import com.shinhan.corebank.subscription.adapter.out.persistence.ProductSubscriptionJpaEntity;
import com.shinhan.corebank.subscription.adapter.out.persistence.ProductSubscriptionJpaRepository;
import com.shinhan.corebank.subscription.api.EarlyTerminationCommand;
import com.shinhan.corebank.subscription.api.EarlyTerminationResult;
import com.shinhan.corebank.subscription.api.EarlyTerminationUseCase;
import com.shinhan.corebank.subscription.application.port.out.MaturityTerminationPasswordVerificationPort;
import com.shinhan.corebank.subscription.domain.MaturityHandling;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

@DisplayName("PH-16 중도해지 실제 DB 통합 테스트")
class EarlyTerminationIntegrationTest extends IntegrationTestSupport {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final long PRODUCT_ACCOUNT_ID = 846_001L;
    private static final long WITHDRAWAL_ACCOUNT_ID = 846_002L;
    private static final long OPENING_LEDGER_ID = 846_000_000_001L;
    private static final long PRINCIPAL = 1_000_000L;

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2038, 8, 16);
    private static final BigDecimal RATE = new BigDecimal("3.65");

    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    @Autowired
    private EarlyTerminationUseCase terminationUseCase;

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

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

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
        LocalDate today = LocalDate.now(clock.withZone(KST));
        openedDate = today.minusDays(120);
        maturityDate = today.plusDays(180);

        originalBusinessDate = businessDateFixture.currentInDb();
        businessDateFixture.moveTo(BUSINESS_DATE);

        customerId = customerFixture.createCustomer();

        productId = productRepository
                .saveAndFlush(ProductTestFixtures.productWithCode(
                        "PH846-" + UUID.randomUUID().toString().substring(0, 8)))
                .getProductId();

        seedAccounts();
        seedOpeningLedger();
        seedSubscription();
    }

    @AfterEach
    void tearDown() {
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
    @DisplayName("정기예금 중도해지 시 구간 이율, 원장, GL, CLOSED 및 감사 이력이 일치한다")
    void terminate_timeDepositSuccess() {
        EarlyTerminationResult result = terminationUseCase.terminate(command());

        long days = ChronoUnit.DAYS.between(openedDate, LocalDate.now(clock.withZone(KST)));
        BigDecimal earlyRate = RATE.multiply(new BigDecimal("0.40"));

        long expectedGross = BigDecimal.valueOf(PRINCIPAL)
                .multiply(BigDecimal.valueOf(days))
                .multiply(earlyRate)
                .divide(BigDecimal.valueOf(36_500), 0, RoundingMode.DOWN)
                .longValueExact();

        assertThat(days).isEqualTo(120);
        assertThat(result.principal()).isEqualTo(PRINCIPAL);
        assertThat(result.grossInterest()).isEqualTo(expectedGross);
        assertThat(result.netInterest()).isPositive();
        assertThat(result.totalTransferred()).isEqualTo(PRINCIPAL + result.netInterest());
        assertThat(result.tradeDate()).isEqualTo(BUSINESS_DATE);

        assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isZero();
        assertThat(balanceOf(WITHDRAWAL_ACCOUNT_ID)).isEqualTo(result.totalTransferred());

        Map<String, Object> account = jdbcTemplate.queryForMap(
                """
                SELECT status, balance, closed_date, closed_trade_date
                FROM account
                WHERE account_id = ?
                """,
                PRODUCT_ACCOUNT_ID);

        assertThat(account.get("status")).isEqualTo("CLOSED");
        assertThat(((Number) account.get("balance")).longValue()).isZero();
        assertThat(account.get("closed_date")).isNotNull();
        assertThat(((java.sql.Date) account.get("closed_trade_date")).toLocalDate())
                .isEqualTo(BUSINESS_DATE);

        List<Map<String, Object>> ledger = jdbcTemplate.queryForList(
                """
                SELECT account_id, direction, amount, balance_after,
                       transaction_number, trade_date
                FROM ledger_entry
                WHERE transaction_type = 'TERMINATION'
                  AND account_id IN (?, ?)
                """,
                PRODUCT_ACCOUNT_ID,
                WITHDRAWAL_ACCOUNT_ID);

        assertThat(ledger).hasSize(2);

        Map<String, Object> withdrawal = ledger.stream()
                .filter(row -> "WITHDRAWAL".equals(row.get("direction")))
                .findFirst()
                .orElseThrow();

        Map<String, Object> deposit = ledger.stream()
                .filter(row -> "DEPOSIT".equals(row.get("direction")))
                .findFirst()
                .orElseThrow();

        assertThat(withdrawal.get("account_id")).isEqualTo(PRODUCT_ACCOUNT_ID);
        assertThat(deposit.get("account_id")).isEqualTo(WITHDRAWAL_ACCOUNT_ID);

        assertThat(((Number) withdrawal.get("amount")).longValue()).isEqualTo(result.totalTransferred());
        assertThat(((Number) deposit.get("amount")).longValue()).isEqualTo(result.totalTransferred());

        assertThat(((Number) withdrawal.get("balance_after")).longValue()).isZero();
        assertThat(withdrawal.get("transaction_number")).isEqualTo(result.transactionNumber());
        assertThat(deposit.get("transaction_number")).isEqualTo(result.transactionNumber());

        assertThat(((java.sql.Date) withdrawal.get("trade_date")).toLocalDate()).isEqualTo(BUSINESS_DATE);

        Map<String, Object> voucher = jdbcTemplate.queryForMap(
                """
                SELECT voucher_no, tx_type, reference_key, trade_date
                FROM gl_voucher
                WHERE tx_type = 'TERMINATION'
                  AND reference_key = ?
                """,
                result.transactionNumber());

        String voucherNo = (String) voucher.get("voucher_no");

        assertThat(voucherNo).matches("\\d{8}-TRM-\\d{6}");
        assertThat(voucher.get("tx_type")).isEqualTo("TERMINATION");
        assertThat(voucher.get("reference_key")).isEqualTo(result.transactionNumber());
        assertThat(((java.sql.Date) voucher.get("trade_date")).toLocalDate()).isEqualTo(BUSINESS_DATE);

        List<Map<String, Object>> journal = jdbcTemplate.queryForList(
                """
                SELECT account_code, dr_cr, amount
                FROM gl_journal_entry
                WHERE voucher_no = ?
                ORDER BY line_no
                """,
                voucherNo);

        assertThat(journal)
                .extracting(row -> row.get("account_code"), row -> row.get("dr_cr"), row -> ((Number) row.get("amount"))
                        .longValue())
                .containsExactly(
                        tuple("20100", "DEBIT", result.totalTransferred()),
                        tuple("20100", "CREDIT", result.totalTransferred()));

        assertThat(countInterestLedger()).isEqualTo(1);
        assertThat(countInterestHistory()).isEqualTo(1);
        assertThat(countVoucher("INTEREST")).isEqualTo(1);
        assertThat(countVoucher("TERMINATION")).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT payment_reason FROM interest_payment WHERE account_id = ?",
                        String.class,
                        PRODUCT_ACCOUNT_ID))
                .isEqualTo("TERMINATION");

        Map<String, Object> history = jdbcTemplate.queryForMap(
                """
                SELECT previous_status, new_status, maturity_date,
                       effective_business_date, trigger_type
                FROM account_status_history
                WHERE account_id = ?
                """,
                PRODUCT_ACCOUNT_ID);

        assertThat(history.get("previous_status")).isEqualTo("ACTIVE");
        assertThat(history.get("new_status")).isEqualTo("CLOSED");
        assertThat(history.get("trigger_type")).isEqualTo("CUSTOMER");

        assertThat(((java.sql.Date) history.get("maturity_date")).toLocalDate()).isEqualTo(maturityDate);
        assertThat(((java.sql.Date) history.get("effective_business_date")).toLocalDate())
                .isEqualTo(BUSINESS_DATE);

        assertThat(ledgerSumOf(PRODUCT_ACCOUNT_ID)).isZero();
        assertThat(ledgerSumOf(WITHDRAWAL_ACCOUNT_ID)).isEqualTo(result.totalTransferred());
    }

    @Test
    @DisplayName("정기적금은 실제 3회 납입 원금으로 중도해지한다")
    void terminate_installmentSavingsSuccess() {
        prepareInstallmentAccount(300_000L);

        insertInstallmentLedger(1, openedDate, 100_000L, 100_000L);
        insertInstallmentLedger(2, openedDate.plusDays(30), 100_000L, 200_000L);
        insertInstallmentLedger(3, openedDate.plusDays(60), 100_000L, 300_000L);

        EarlyTerminationResult result = terminationUseCase.terminate(command());

        assertThat(result.principal()).isEqualTo(300_000L);
        assertThat(result.netInterest()).isPositive();
        assertThat(result.totalTransferred()).isEqualTo(300_000L + result.netInterest());

        assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isZero();
        assertThat(balanceOf(WITHDRAWAL_ACCOUNT_ID)).isEqualTo(result.totalTransferred());

        assertThat(countTerminationLedger()).isEqualTo(2);
        assertThat(countVoucher("TERMINATION")).isEqualTo(1);
        assertThat(countClosureHistory()).isEqualTo(1);

        assertThat(ledgerSumOf(PRODUCT_ACCOUNT_ID)).isZero();
        assertThat(accountStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("이미 중도해지한 계좌에는 이자와 원리금이 재지급되지 않는다")
    void terminate_duplicateRejected() {
        terminationUseCase.terminate(command());

        assertThatThrownBy(() -> terminationUseCase.terminate(command())).isInstanceOf(BusinessException.class);

        assertThat(countInterestHistory()).isEqualTo(1);
        assertThat(countInterestLedger()).isEqualTo(1);
        assertThat(countTerminationLedger()).isEqualTo(2);
        assertThat(countVoucher("INTEREST")).isEqualTo(1);
        assertThat(countVoucher("TERMINATION")).isEqualTo(1);
        assertThat(countClosureHistory()).isEqualTo(1);

        assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isZero();
        assertThat(accountStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("만기일이 이미 도래한 계좌는 중도해지할 수 없다")
    void terminate_maturityReachedRejected() {
        LocalDate today = LocalDate.now(clock.withZone(KST));

        jdbcTemplate.update("UPDATE account SET maturity_date = ? WHERE account_id = ?", today, PRODUCT_ACCOUNT_ID);

        jdbcTemplate.update(
                "UPDATE product_subscription SET maturity_date = ? WHERE account_id = ?", today, PRODUCT_ACCOUNT_ID);

        entityManager.clear();

        assertThatThrownBy(() -> terminationUseCase.terminate(command()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(AccountErrorCode.EARLY_TERMINATION_NOT_ALLOWED));

        assertThat(accountStatus()).isEqualTo("ACTIVE");
        assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isEqualTo(PRINCIPAL);
        assertThat(balanceOf(WITHDRAWAL_ACCOUNT_ID)).isZero();
        assertThat(countInterestHistory()).isZero();
        assertThat(countTerminationLedger()).isZero();
        assertThat(countClosureHistory()).isZero();
    }

    @Test
    @DisplayName("감사 이력 저장 후 실패하면 이자, 원리금, GL, 계좌 상태가 모두 롤백된다")
    void terminate_historyFailureRollsBack() {
        AccountStatusHistoryPersistenceAdapter targetSpy = AopTestUtils.getUltimateTargetObject(historyAdapter);

        doAnswer(invocation -> {
                    invocation.callRealMethod();
                    throw new IllegalStateException("중도해지 감사 이력 실패 주입");
                })
                .when(targetSpy)
                .recordEarlyClosure(
                        eq(PRODUCT_ACCOUNT_ID), eq(maturityDate), eq(BUSINESS_DATE), any(LocalDateTime.class));

        assertThatThrownBy(() -> terminationUseCase.terminate(command()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("중도해지 감사 이력 실패 주입");

        assertThat(accountStatus()).isEqualTo("ACTIVE");
        assertThat(balanceOf(PRODUCT_ACCOUNT_ID)).isEqualTo(PRINCIPAL);
        assertThat(balanceOf(WITHDRAWAL_ACCOUNT_ID)).isZero();

        assertThat(countInterestLedger()).isZero();
        assertThat(countInterestHistory()).isZero();
        assertThat(countTerminationLedger()).isZero();
        assertThat(countVoucher("INTEREST")).isZero();
        assertThat(countVoucher("TERMINATION")).isZero();
        assertThat(countClosureHistory()).isZero();

        assertThat(ledgerSumOf(PRODUCT_ACCOUNT_ID)).isEqualTo(PRINCIPAL);

        Map<String, Object> account = jdbcTemplate.queryForMap(
                """
                SELECT closed_date, closed_trade_date
                FROM account
                WHERE account_id = ?
                """,
                PRODUCT_ACCOUNT_ID);

        assertThat(account.get("closed_date")).isNull();
        assertThat(account.get("closed_trade_date")).isNull();
    }

    private EarlyTerminationCommand command() {
        return new EarlyTerminationCommand(customerId, PRODUCT_ACCOUNT_ID, "test-account-password-token");
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
                    ?, '846000000001', ?, ?,
                    'TIME_DEPOSIT', ?, 0, 'ACTIVE',
                    ?, FALSE, ?, ?, NOW(6), NOW(6)
                )
                """,
                PRODUCT_ACCOUNT_ID,
                customerId,
                productId,
                PRINCIPAL,
                PASSWORD_HASH,
                openedDate.atTime(9, 0),
                maturityDate);

        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, account_number, customer_id, product_id,
                    account_type, balance, hold_amount, status,
                    password_hash, withdrawal_registered,
                    withdrawal_registered_at, opened_date,
                    created_at, updated_at
                ) VALUES (
                    ?, '846000000002', ?, NULL,
                    'DEMAND_DEPOSIT', 0, 0, 'ACTIVE',
                    ?, TRUE, NOW(6), ?, NOW(6), NOW(6)
                )
                """,
                WITHDRAWAL_ACCOUNT_ID,
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

    private void prepareInstallmentAccount(long balance) {
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
                UPDATE product_subscription
                SET subscription_amount = 100000
                WHERE account_id = ?
                """,
                PRODUCT_ACCOUNT_ID);

        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id = ?", PRODUCT_ACCOUNT_ID);

        jdbcTemplate.update(
                """
                UPDATE account
                SET account_type = 'INSTALLMENT_SAVINGS',
                    balance = ?
                WHERE account_id = ?
                """,
                balance,
                PRODUCT_ACCOUNT_ID);

        entityManager.clear();
    }

    private void insertInstallmentLedger(int sequence, LocalDate paidDate, long amount, long balanceAfter) {

        String transactionNumber =
                paidDate.format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("BT%010d", sequence);

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
                    ?, 'AUTO_TRANSFER',
                    '적금납입', 'BT',
                    FALSE, NULL, ?, ?
                )
                """,
                OPENING_LEDGER_ID + sequence,
                PRODUCT_ACCOUNT_ID,
                transactionNumber,
                amount,
                balanceAfter,
                paidDate.atTime(9, 0),
                paidDate);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private String accountStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM account WHERE account_id = ?", String.class, PRODUCT_ACCOUNT_ID);
    }

    private long countInterestLedger() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM ledger_entry
                WHERE account_id = ?
                  AND transaction_type = 'INTEREST'
                """,
                Long.class,
                PRODUCT_ACCOUNT_ID);
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

    private long countInterestHistory() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interest_payment WHERE account_id = ?", Long.class, PRODUCT_ACCOUNT_ID);
    }

    private long countVoucher(String txType) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM gl_voucher
                WHERE trade_date = ? AND tx_type = ?
                """,
                Long.class,
                BUSINESS_DATE,
                txType);
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
}
