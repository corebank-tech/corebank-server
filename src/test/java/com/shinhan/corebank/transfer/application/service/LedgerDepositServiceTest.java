package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.transfer.api.DepositChannel;
import com.shinhan.corebank.transfer.api.DepositType;
import com.shinhan.corebank.transfer.api.LedgerDepositResult;
import com.shinhan.corebank.transfer.api.LedgerDepositUseCase;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 예적금 입금 계약(#595) 통합 테스트. 호출자 롤백을 실제 커밋 경계로 확인해야 해서 @Transactional을 두지 않는다.
 */
@DisplayName("LedgerDepositService — 예적금 이자·만기·해지 입금 계약")
class LedgerDepositServiceTest extends IntegrationTestSupport {

    // 시드·다른 테스트와 겹치지 않게 이슈 번호 대역을 쓴다.
    private static final long CUSTOMER_ID = 595L;
    private static final long CUSTOMER_ACCOUNT = 59501L;
    private static final long PRODUCT_ACCOUNT = 59502L;
    // 원장 ID는 생성기가 별도 시퀀스 테이블로 내므로, 시드 행은 그 범위에서 먼 값으로 고정한다.
    private static final long OPENING_LEDGER_ID_BASE = 595_000_000_000L;
    private static final long CUSTOMER_OPENING = 100_000L;
    private static final long PRINCIPAL = 1_000_000L;

    @Autowired
    private LedgerDepositUseCase ledgerDepositUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanUp();
        seed();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("만기해지: MATURED 상품 계좌에서 고객 계좌로 옮기고 MATURITY 원장 2행을 남긴다")
    void transferBetween_maturity_movesMoneyAndPostsTwoRows() {
        // given
        setStatus(PRODUCT_ACCOUNT, "MATURED");

        // when
        LedgerDepositResult result = ledgerDepositUseCase.transferBetween(
                PRODUCT_ACCOUNT, CUSTOMER_ACCOUNT, PRINCIPAL, DepositType.MATURITY, DepositChannel.BT);

        // then
        assertThat(result.depositBalanceAfter()).isEqualTo(CUSTOMER_OPENING + PRINCIPAL);
        assertThat(balanceOf(PRODUCT_ACCOUNT)).isZero();
        assertThat(balanceOf(CUSTOMER_ACCOUNT)).isEqualTo(CUSTOMER_OPENING + PRINCIPAL);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT account_id, direction, amount, transaction_type, transaction_content, channel, transfer_id"
                        + " FROM ledger_entry WHERE transaction_number = ? ORDER BY direction",
                result.transactionNumber());
        assertThat(rows).hasSize(2);
        assertThat(rows)
                .extracting(r -> r.get("account_id"), r -> r.get("direction"))
                .containsExactly(tuple(CUSTOMER_ACCOUNT, "DEPOSIT"), tuple(PRODUCT_ACCOUNT, "WITHDRAWAL"));
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.get("amount")).isEqualTo(PRINCIPAL);
            assertThat(r.get("transaction_type")).isEqualTo("MATURITY");
            assertThat(r.get("transaction_content")).isEqualTo("만기해지");
            assertThat(r.get("channel")).isEqualTo("BT");
            assertThat(r.get("transfer_id")).isNull();
        });
        assertThat(result.transactionNumber()).contains("BT");
    }

    @Test
    @DisplayName("이자: 상대 계좌 없이 INTEREST 입금 1행을 남긴다")
    void credit_interest_postsSingleRow() {
        // when
        LedgerDepositResult result =
                ledgerDepositUseCase.credit(PRODUCT_ACCOUNT, 20_000L, DepositType.INTEREST, DepositChannel.BT);

        // then
        assertThat(result.depositBalanceAfter()).isEqualTo(PRINCIPAL + 20_000L);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT account_id, direction, transaction_type, transaction_content FROM ledger_entry"
                        + " WHERE transaction_number = ?",
                result.transactionNumber());
        assertThat(rows).singleElement().satisfies(r -> {
            assertThat(r.get("account_id")).isEqualTo(PRODUCT_ACCOUNT);
            assertThat(r.get("direction")).isEqualTo("DEPOSIT");
            assertThat(r.get("transaction_type")).isEqualTo("INTEREST");
            assertThat(r.get("transaction_content")).isEqualTo("이자");
        });
    }

    @Test
    @DisplayName("만기 시점 이자 → 원리금 이동 뒤 두 계좌 모두 잔액 = 원장 합계(전수 대사 기준)")
    void balanceEqualsLedgerSum_afterInterestThenMaturity() {
        // given
        setStatus(PRODUCT_ACCOUNT, "MATURED");

        // when: 한 트랜잭션에서 이자를 넣고 원리금을 옮긴다(P2 만기해지 순서)
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ledgerDepositUseCase.credit(PRODUCT_ACCOUNT, 20_000L, DepositType.INTEREST, DepositChannel.BT);
            ledgerDepositUseCase.transferBetween(
                    PRODUCT_ACCOUNT, CUSTOMER_ACCOUNT, PRINCIPAL + 20_000L, DepositType.MATURITY, DepositChannel.BT);
        });

        // then
        assertThat(balanceOf(PRODUCT_ACCOUNT)).isZero().isEqualTo(ledgerSumOf(PRODUCT_ACCOUNT));
        assertThat(balanceOf(CUSTOMER_ACCOUNT))
                .isEqualTo(CUSTOMER_OPENING + PRINCIPAL + 20_000L)
                .isEqualTo(ledgerSumOf(CUSTOMER_ACCOUNT));
    }

    @Test
    @DisplayName("호출자 트랜잭션이 롤백되면 잔액과 원장도 함께 롤백된다")
    void callerRollback_rollsBackBalanceAndLedger() {
        // given
        TransactionTemplate caller = new TransactionTemplate(transactionManager);

        // when: 입금 뒤 호출자 쪽 후속 단계(상태 전이 등)가 실패한다
        assertThatThrownBy(() -> caller.executeWithoutResult(status -> {
                    ledgerDepositUseCase.transferBetween(
                            PRODUCT_ACCOUNT, CUSTOMER_ACCOUNT, PRINCIPAL, DepositType.TERMINATION, DepositChannel.WB);
                    ledgerDepositUseCase.credit(CUSTOMER_ACCOUNT, 5_000L, DepositType.INTEREST, DepositChannel.WB);
                    throw new IllegalStateException("CLOSED 전이 실패");
                }))
                .isInstanceOf(IllegalStateException.class);

        // then
        assertThat(balanceOf(PRODUCT_ACCOUNT)).isEqualTo(PRINCIPAL);
        assertThat(balanceOf(CUSTOMER_ACCOUNT)).isEqualTo(CUSTOMER_OPENING);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (?, ?) AND transaction_type <> 'OPENING'",
                        Long.class,
                        PRODUCT_ACCOUNT,
                        CUSTOMER_ACCOUNT))
                .isZero();
    }

    @Test
    @DisplayName("상품 계좌가 ACTIVE·MATURED가 아니면 TRF0304로 거부한다")
    void transferBetween_rejectsInactiveFromAccount() {
        setStatus(PRODUCT_ACCOUNT, "SUSPENDED");

        assertRejected(
                () -> ledgerDepositUseCase.transferBetween(
                        PRODUCT_ACCOUNT, CUSTOMER_ACCOUNT, PRINCIPAL, DepositType.TERMINATION, DepositChannel.WB),
                TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("받는 고객 계좌가 ACTIVE가 아니면 TRF0301로 거부한다")
    void transferBetween_rejectsInactiveToAccount() {
        setStatus(CUSTOMER_ACCOUNT, "MATURED");

        assertRejected(
                () -> ledgerDepositUseCase.transferBetween(
                        PRODUCT_ACCOUNT, CUSTOMER_ACCOUNT, PRINCIPAL, DepositType.TERMINATION, DepositChannel.WB),
                TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("상품 계좌 잔액보다 많이 옮기려 하면 TRF0303으로 거부한다")
    void transferBetween_rejectsInsufficientBalance() {
        assertRejected(
                () -> ledgerDepositUseCase.transferBetween(
                        PRODUCT_ACCOUNT, CUSTOMER_ACCOUNT, PRINCIPAL + 1, DepositType.TERMINATION, DepositChannel.WB),
                TransferErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("이자를 받을 계좌가 ACTIVE·MATURED가 아니면 TRF0301로 거부한다")
    void credit_rejectsInactiveAccount() {
        setStatus(PRODUCT_ACCOUNT, "CLOSED");

        assertRejected(
                () -> ledgerDepositUseCase.credit(PRODUCT_ACCOUNT, 20_000L, DepositType.INTEREST, DepositChannel.BT),
                TransferErrorCode.PAYEE_ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("메서드와 맞지 않는 업무 구분은 CMN0001로 거부한다")
    void rejectsTypeNotMatchingMethod() {
        assertRejected(
                () -> ledgerDepositUseCase.credit(PRODUCT_ACCOUNT, 20_000L, DepositType.MATURITY, DepositChannel.BT),
                CommonErrorCode.INVALID_INPUT);
        assertRejected(
                () -> ledgerDepositUseCase.transferBetween(
                        PRODUCT_ACCOUNT, CUSTOMER_ACCOUNT, PRINCIPAL, DepositType.INTEREST, DepositChannel.BT),
                CommonErrorCode.INVALID_INPUT);
    }

    private void assertRejected(Runnable call, Object errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
        // 거부되면 잔액도 원장도 그대로다
        assertThat(balanceOf(PRODUCT_ACCOUNT)).isEqualTo(PRINCIPAL);
        assertThat(balanceOf(CUSTOMER_ACCOUNT)).isEqualTo(CUSTOMER_OPENING);
    }

    private void seed() {
        jdbcTemplate.update(
                """
            INSERT INTO customer (customer_id, user_id, password_hash, user_name, birth_date, email, phone_number, joined_at, created_at, updated_at)
            VALUES (?, 'issue595', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', '테스터', '1990-01-01', 'issue595@test.com', '01059500000', NOW(6), NOW(6), NOW(6))
            """,
                CUSTOMER_ID);
        // 상품 계좌는 입출금으로 둔다 — 입금 계약은 계좌 종류를 보지 않고, 예·적금은 상품·만기일 시드가 따로 필요하다.
        jdbcTemplate.update(
                """
            INSERT INTO account (account_id, account_number, customer_id, product_id, account_type, balance, status, password_hash, withdrawal_registered, withdrawal_registered_at, opened_date, created_at, updated_at)
            VALUES (?, '595000000001', ?, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', TRUE, NOW(6), '2026-08-01', NOW(6), NOW(6)),
                   (?, '595000000002', ?, NULL, 'DEMAND_DEPOSIT', ?, 'ACTIVE', '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklm', FALSE, NULL, '2026-08-01', NOW(6), NOW(6))
            """,
                CUSTOMER_ACCOUNT,
                CUSTOMER_ID,
                CUSTOMER_OPENING,
                PRODUCT_ACCOUNT,
                CUSTOMER_ID,
                PRINCIPAL);
        // 잔액 = 원장 합계에서 출발하도록 개시 잔액을 원장에도 남긴다.
        insertOpening(1, CUSTOMER_ACCOUNT, CUSTOMER_OPENING);
        insertOpening(2, PRODUCT_ACCOUNT, PRINCIPAL);
    }

    private void insertOpening(int seq, long accountId, long amount) {
        jdbcTemplate.update(
                """
            INSERT INTO ledger_entry (ledger_entry_id, account_id, transfer_id, transaction_number, direction, amount, balance_after,
                                      transaction_type, transaction_content, channel, reversed, reversal_id, occurred_at)
            VALUES (?, ?, NULL, ?, 'DEPOSIT', ?, ?, 'OPENING', '개시', 'BT', FALSE, NULL, '2026-09-01 00:00:00')
            """,
                OPENING_LEDGER_ID_BASE + seq,
                accountId,
                "20260901BT959500000" + seq,
                amount,
                amount);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (?, ?)", CUSTOMER_ACCOUNT, PRODUCT_ACCOUNT);
        jdbcTemplate.update("DELETE FROM account WHERE account_id IN (?, ?)", CUSTOMER_ACCOUNT, PRODUCT_ACCOUNT);
        jdbcTemplate.update("DELETE FROM customer WHERE customer_id = ?", CUSTOMER_ID);
    }

    private void setStatus(long accountId, String status) {
        // CLOSED는 해지일이 있어야 ck_account_closed_date를 통과한다.
        jdbcTemplate.update(
                "UPDATE account SET status = ?, closed_date = IF(? = 'CLOSED', NOW(6), NULL) WHERE account_id = ?",
                status,
                status,
                accountId);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private long ledgerSumOf(long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(CASE direction WHEN 'DEPOSIT' THEN amount ELSE -amount END), 0)"
                        + " FROM ledger_entry WHERE account_id = ?",
                Long.class,
                accountId);
    }
}
