package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.event.DomainEventSink;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("PH-15 MATURED 계좌 일반 신규 거래 차단 DB 통합 테스트")
class MaturedAccountTransactionRestrictionIntegrationTest extends IntegrationTestSupport {

    private static final long CUSTOMER_ID = 735_001L;
    private static final long PRODUCT_ID = 735_001L;

    private static final long ACTIVE_SOURCE = 735_001_001L;
    private static final long MATURED_SAVINGS = 735_001_002L;
    private static final long ACTIVE_PAYEE = 735_001_003L;

    private static final String ACTIVE_SOURCE_NUMBER = "110735001001";
    private static final String MATURED_NUMBER = "110735001002";
    private static final String ACTIVE_PAYEE_NUMBER = "110735001003";

    private static final long INITIAL_BALANCE = 100_000L;
    private static final long TRANSFER_AMOUNT = 10_000L;
    private static final long OPENING_LEDGER_BASE = 735_001_000_000L;

    private static final String PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm";

    @Autowired
    private TransferExecutionUseCase transferExecutionUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 인증 토큰 발급 자체가 아니라 계좌 상태 검증을 테스트한다.
    @MockitoBean
    private AccountPasswordAuthTokenVerifier passwordTokenVerifier;

    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    // 외부 이벤트 저장을 테스트 범위에서 분리한다.
    @MockitoBean
    private DomainEventSink domainEventSink;

    @BeforeEach
    void setUp() {
        cleanUp();
        seedCustomer();
        seedTransferLimit();
        seedProduct();
        seedAccounts();
        seedOpeningLedgers();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @ParameterizedTest(name = "{0}: MATURED 정기적금 계좌로 신규 입금 거부")
    @EnumSource(TransferType.class)
    void rejectDepositToMaturedAccount(TransferType type) {
        // ACTIVE 출금계좌 -> MATURED 정기적금 계좌
        assertRejectedWithoutMoneyMovement(
                type, ACTIVE_SOURCE, MATURED_NUMBER, TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED);
    }

    @ParameterizedTest(name = "{0}: MATURED 정기적금 계좌의 일반 출금 거부")
    @EnumSource(TransferType.class)
    void rejectWithdrawalFromMaturedAccount(TransferType type) {
        // 예적금은 출금계좌로 등록할 수 없으므로
        // 상태 검사보다 등록 여부 검사에서 먼저 거부된다.
        assertRejectedWithoutMoneyMovement(
                type, MATURED_SAVINGS, ACTIVE_PAYEE_NUMBER, TransferErrorCode.WITHDRAWAL_ACCOUNT_NOT_REGISTERED);
    }

    private void assertRejectedWithoutMoneyMovement(
            TransferType type, long withdrawalAccountId, String depositAccountNumber, TransferErrorCode expectedError) {

        long ledgerCountBefore = countLedger();
        long transferCountBefore = countTransferRows();
        long usageBefore = usedTransferLimit();

        TransferCommand command = command(type, withdrawalAccountId, depositAccountNumber);

        TransferResult result = transferExecutionUseCase.execute(command);

        // 1. 거래 거부 및 오류코드 확인
        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(expectedError.getCode());

        // 2. 실패 시점에 따른 거래 기록 확인
        if (expectedError == TransferErrorCode.WITHDRAWAL_ACCOUNT_NOT_REGISTERED) {
            // 사전검증 실패: 채번 전이므로 transfer 행이 생성되지 않는다.
            assertThat(result.transactionNumber()).isNull();
            assertThat(countTransferRows()).isEqualTo(transferCountBefore);
        } else {
            // 채번 이후 실패: ERROR 행을 독립 트랜잭션으로 저장한다.
            assertThat(result.transactionNumber()).isNotBlank();
            assertThat(countTransferRows()).isEqualTo(transferCountBefore + 1);

            Map<String, Object> transfer = jdbcTemplate.queryForMap(
                    """
            SELECT status, error_code
            FROM transfer
            WHERE transaction_number = ?
            """,
                    result.transactionNumber());

            assertThat(transfer.get("status")).isEqualTo("ERROR");
            assertThat(transfer.get("error_code")).isEqualTo(expectedError.getCode());

            // 실패 거래번호로 원장이나 GL이 기표되지 않았는지 확인
            assertThat(jdbcTemplate.queryForObject(
                            """
            SELECT COUNT(*)
            FROM ledger_entry
            WHERE transaction_number = ?
            """,
                            Long.class,
                            result.transactionNumber()))
                    .isZero();

            assertThat(jdbcTemplate.queryForObject(
                            """
            SELECT COUNT(*)
            FROM gl_voucher
            WHERE reference_key = ?
            """,
                            Long.class,
                            result.transactionNumber()))
                    .isZero();

            assertThat(jdbcTemplate.queryForObject(
                            """
            SELECT COUNT(*)
            FROM gl_journal_entry j
            JOIN gl_voucher v ON j.voucher_no = v.voucher_no
            WHERE v.reference_key = ?
            """,
                            Long.class,
                            result.transactionNumber()))
                    .isZero();
        }

        // 3. 실패한 거래는 잔액을 변경하지 않는다.
        assertThat(balanceOf(ACTIVE_SOURCE)).isEqualTo(INITIAL_BALANCE);
        assertThat(balanceOf(MATURED_SAVINGS)).isEqualTo(INITIAL_BALANCE);
        assertThat(balanceOf(ACTIVE_PAYEE)).isEqualTo(INITIAL_BALANCE);

        // 4. 신규 원장 없음
        assertThat(countLedger()).isEqualTo(ledgerCountBefore);

        // 5. 일일 이체한도 사용액 변경 없음
        assertThat(usedTransferLimit()).isEqualTo(usageBefore);

        // 6. 만기 계좌 상태 유지
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM account WHERE account_id = ?", String.class, MATURED_SAVINGS))
                .isEqualTo("MATURED");
    }

    private TransferCommand command(TransferType type, long withdrawalAccountId, String depositAccountNumber) {

        boolean immediate = type == TransferType.IMMEDIATE;

        return TransferCommand.builder()
                .customerId(CUSTOMER_ID)
                .withdrawalAccountId(withdrawalAccountId)
                .depositAccountNumber(depositAccountNumber)
                .amount(TRANSFER_AMOUNT)
                .transferType(type)
                .channel(immediate ? TransferChannel.WB : TransferChannel.BT)
                .sourceId(immediate ? null : type == TransferType.AUTO ? 735_001L : 735_002L)
                .executionDate(immediate ? null : LocalDate.of(2026, 10, 23))
                .authToken(immediate ? "test-password-token" : null)
                .otpAuthToken(immediate ? "test-otp-token" : null)
                .myPassbookMemo("출금")
                .recipientPassbookMemo("입금")
                .build();
    }

    private void seedCustomer() {
        jdbcTemplate.update(
                """
                INSERT INTO customer (
                    customer_id, user_id, password_hash,
                    user_name, birth_date, email, phone_number,
                    joined_at, created_at, updated_at
                ) VALUES (
                    ?, 'ph735user', ?, '테스터',
                    '1990-01-01', 'ph735@test.com', '01073500001',
                    NOW(6), NOW(6), NOW(6)
                )
                """,
                CUSTOMER_ID,
                PASSWORD_HASH);
    }

    private void seedTransferLimit() {
        jdbcTemplate.update(
                """
                INSERT INTO transfer_limit (
                    customer_id, one_time_limit, daily_limit,
                    created_at, updated_at
                ) VALUES (?, 1000000, 5000000, NOW(6), NOW(6))
                """,
                CUSTOMER_ID);
    }

    private void seedProduct() {
        jdbcTemplate.update(
                """
                INSERT INTO product (
                    product_id, product_code, product_name,
                    product_group, deposit_type,
                    base_rate, max_rate,
                    min_amount, max_amount, amount_unit,
                    min_term_months, max_term_months,
                    interest_pay_type, sale_status,
                    created_at, updated_at
                ) VALUES (
                    ?, 'PH735-SAVINGS', 'PH735 정기적금',
                    'SAVINGS', 'INSTALLMENT',
                    2.50, 3.00,
                    100000, 100000000, 10000,
                    6, 36,
                    'SIMPLE', 'ON_SALE',
                    NOW(6), NOW(6)
                )
                """,
                PRODUCT_ID);
    }

    private void seedAccounts() {
        // 정상 출금계좌
        seedAccount(ACTIVE_SOURCE, ACTIVE_SOURCE_NUMBER, null, "DEMAND_DEPOSIT", "ACTIVE", true, null);

        // 만기 도달 정기적금 계좌
        // 정기적금은 DB 제약상 출금계좌로 등록할 수 없다.
        seedAccount(
                MATURED_SAVINGS,
                MATURED_NUMBER,
                PRODUCT_ID,
                "INSTALLMENT_SAVINGS",
                "MATURED",
                false,
                LocalDate.of(2026, 9, 1));

        // 정상 수취계좌
        seedAccount(ACTIVE_PAYEE, ACTIVE_PAYEE_NUMBER, null, "DEMAND_DEPOSIT", "ACTIVE", false, null);
    }

    private void seedAccount(
            long accountId,
            String accountNumber,
            Long productId,
            String accountType,
            String status,
            boolean withdrawalRegistered,
            LocalDate maturityDate) {

        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, account_number, customer_id,
                    product_id, account_type, balance, hold_amount,
                    status, password_hash,
                    withdrawal_registered, withdrawal_registered_at,
                    opened_date, maturity_date,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, 0,
                    ?, ?, ?, ?,
                    '2026-03-01 09:00:00', ?,
                    NOW(6), NOW(6)
                )
                """,
                accountId,
                accountNumber,
                CUSTOMER_ID,
                productId,
                accountType,
                INITIAL_BALANCE,
                status,
                PASSWORD_HASH,
                withdrawalRegistered,
                withdrawalRegistered ? java.sql.Timestamp.valueOf("2026-03-01 09:00:00") : null,
                maturityDate);
    }

    private void seedOpeningLedgers() {
        seedOpening(1, ACTIVE_SOURCE);
        seedOpening(2, MATURED_SAVINGS);
        seedOpening(3, ACTIVE_PAYEE);
    }

    private void seedOpening(int sequence, long accountId) {
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
                    FALSE, NULL, '2026-09-01 08:00:00',
                    '2026-09-01'
                )
                """,
                OPENING_LEDGER_BASE + sequence,
                accountId,
                String.format("20260901BT%010d", 735_000 + sequence),
                INITIAL_BALANCE,
                INITIAL_BALANCE);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private long countLedger() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ledger_entry
                WHERE account_id IN (?, ?, ?)
                """,
                Long.class,
                ACTIVE_SOURCE,
                MATURED_SAVINGS,
                ACTIVE_PAYEE);
    }

    private long usedTransferLimit() {
        return jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(used_amount), 0)
                FROM transfer_limit_daily_usage
                WHERE customer_id = ?
                """,
                Long.class,
                CUSTOMER_ID);
    }

    private long countTransferRows() {
        return jdbcTemplate.queryForObject(
                """
            SELECT COUNT(*)
            FROM transfer
            WHERE withdrawal_account_id IN (?, ?, ?)
               OR deposit_account_id IN (?, ?, ?)
            """,
                Long.class,
                ACTIVE_SOURCE,
                MATURED_SAVINGS,
                ACTIVE_PAYEE,
                ACTIVE_SOURCE,
                MATURED_SAVINGS,
                ACTIVE_PAYEE);
    }

    private void cleanUp() {
        // 혹시 금융 기표가 발생했더라도 테스트 데이터가
        // 다음 테스트에 남지 않도록 관계 순서대로 정리한다.
        List<String> transactionNumbers = jdbcTemplate.queryForList(
                """
                SELECT transaction_number
                FROM transfer
                WHERE withdrawal_account_id IN (?, ?, ?)
                   OR deposit_account_id IN (?, ?, ?)
                """,
                String.class,
                ACTIVE_SOURCE,
                MATURED_SAVINGS,
                ACTIVE_PAYEE,
                ACTIVE_SOURCE,
                MATURED_SAVINGS,
                ACTIVE_PAYEE);

        for (String txNo : transactionNumbers) {
            jdbcTemplate.update(
                    """
                    DELETE FROM gl_journal_entry
                    WHERE voucher_no IN (
                        SELECT voucher_no
                        FROM gl_voucher
                        WHERE reference_key = ?
                    )
                    """,
                    txNo);

            jdbcTemplate.update("DELETE FROM gl_voucher WHERE reference_key = ?", txNo);
        }

        jdbcTemplate.update(
                """
                DELETE FROM ledger_entry
                WHERE account_id IN (?, ?, ?)
                """,
                ACTIVE_SOURCE,
                MATURED_SAVINGS,
                ACTIVE_PAYEE);

        jdbcTemplate.update(
                """
                DELETE FROM transfer
                WHERE withdrawal_account_id IN (?, ?, ?)
                   OR deposit_account_id IN (?, ?, ?)
                """,
                ACTIVE_SOURCE,
                MATURED_SAVINGS,
                ACTIVE_PAYEE,
                ACTIVE_SOURCE,
                MATURED_SAVINGS,
                ACTIVE_PAYEE);

        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = ?", CUSTOMER_ID);

        jdbcTemplate.update(
                "DELETE FROM account WHERE account_id IN (?, ?, ?)", ACTIVE_SOURCE, MATURED_SAVINGS, ACTIVE_PAYEE);

        jdbcTemplate.update("DELETE FROM product WHERE product_id = ?", PRODUCT_ID);

        jdbcTemplate.update("DELETE FROM transfer_limit WHERE customer_id = ?", CUSTOMER_ID);

        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }
}
