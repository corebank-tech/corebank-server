package com.shinhan.corebank.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.PartialStopResult;
import com.shinhan.corebank.account.api.PaymentStopUseCase;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.account.support.CustomerTestFixture;
import com.shinhan.corebank.common.exception.BusinessException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

// 계약은 호출자 트랜잭션 안에서만 돈다(MANDATORY). 검증은 커밋 뒤 JDBC로 다시 읽어 영속성 컨텍스트 캐시를 피한다.
class PaymentStopServiceIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime OPENED_AT = LocalDateTime.of(2026, 1, 2, 9, 0);
    private static final String PASSWORD_HASH = "$2a$10$34abEWY4uXLwTEnT5hNow.603a5rWofFx7Bnj59agU.PsESK0v/Yq";

    // 사건 ID는 테스트 전용 대역(928_3xx). paymentstop 테이블이 없어 FK는 걸리지 않는다.
    private static final long CASE_A = 928_301L;
    private static final long CASE_B = 928_302L;

    @Autowired
    private PaymentStopUseCase paymentStopUseCase;

    @Autowired
    private CustomerTestFixture customerTestFixture;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long customerId;
    private int accountSequence;

    @BeforeEach
    void setUp() {
        customerId = customerTestFixture.createCustomer();
        accountSequence = 0;
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE p FROM account_payment_stop p JOIN account a ON a.account_id = p.account_id WHERE a.customer_id = ?",
                customerId);
        jdbcTemplate.update(
                "DELETE h FROM account_status_history h JOIN account a ON a.account_id = h.account_id WHERE a.customer_id = ?",
                customerId);
        jdbcTemplate.update("DELETE FROM account WHERE customer_id = ?", customerId);
        customerTestFixture.deleteCustomer(customerId);
    }

    @Test
    @DisplayName("전부 지급정지를 걸면 SUSPENDED가 되고 상태 이력이 남는다")
    void applyFullStop_suspends() {
        long accountId = insertAccount(AccountStatus.ACTIVE, 100_000L);

        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A));

        assertThat(status(accountId)).isEqualTo("SUSPENDED");
        assertThat(holdAmount(accountId)).isZero();
        assertThat(history(accountId)).containsExactly("ACTIVE>SUSPENDED:PAYMENT_STOP");
        assertThat(stopRow(accountId, CASE_A)).isEqualTo("FULL,0,0,ACTIVE,1");
    }

    @Test
    @DisplayName("일부 지급정지로 바꾸면 ACTIVE로 돌아오고 요청액만큼 보류액이 묶인다")
    void convertToPartial_holdsRequestedAmount() {
        long accountId = insertAccount(AccountStatus.ACTIVE, 100_000L);
        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A));

        PartialStopResult result = inTx(() -> paymentStopUseCase.convertToPartial(accountId, CASE_A, 30_000L));

        assertThat(result).isEqualTo(new PartialStopResult(30_000L, 30_000L));
        assertThat(status(accountId)).isEqualTo("ACTIVE");
        assertThat(holdAmount(accountId)).isEqualTo(30_000L);
        assertThat(history(accountId))
                .containsExactly("ACTIVE>SUSPENDED:PAYMENT_STOP", "SUSPENDED>ACTIVE:PAYMENT_STOP");
    }

    @Test
    @DisplayName("해제하면 정지 전 상태로 돌아오고 보류액이 풀린다")
    void release_restoresStatusBefore() {
        long accountId = insertAccount(AccountStatus.ACTIVE, 100_000L);
        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A));
        inTx(() -> paymentStopUseCase.convertToPartial(accountId, CASE_A, 30_000L));

        inTx(() -> paymentStopUseCase.release(accountId, CASE_A));

        assertThat(status(accountId)).isEqualTo("ACTIVE");
        assertThat(holdAmount(accountId)).isZero();
        assertThat(stopActive(accountId, CASE_A)).isFalse();
    }

    @Test
    @DisplayName("두 사건 중 하나를 해제해도 다른 사건의 지급정지는 유지된다")
    void release_keepsOtherCase() {
        long accountId = insertAccount(AccountStatus.ACTIVE, 100_000L);
        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A));
        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_B));

        inTx(() -> paymentStopUseCase.release(accountId, CASE_A));

        assertThat(status(accountId)).isEqualTo("SUSPENDED");

        inTx(() -> paymentStopUseCase.release(accountId, CASE_B));

        assertThat(status(accountId)).isEqualTo("ACTIVE");
        assertThat(history(accountId))
                .containsExactly("ACTIVE>SUSPENDED:PAYMENT_STOP", "SUSPENDED>ACTIVE:PAYMENT_STOP");
    }

    @Test
    @DisplayName("일부 지급정지 사건만 해제하면 다른 사건의 전부 지급정지가 남아 SUSPENDED를 유지한다")
    void release_partialCaseKeepsFullStop() {
        long accountId = insertAccount(AccountStatus.ACTIVE, 100_000L);
        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A));
        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_B));
        inTx(() -> paymentStopUseCase.convertToPartial(accountId, CASE_A, 20_000L));

        assertThat(status(accountId)).isEqualTo("SUSPENDED");
        assertThat(holdAmount(accountId)).isEqualTo(20_000L);

        inTx(() -> paymentStopUseCase.release(accountId, CASE_A));

        assertThat(status(accountId)).isEqualTo("SUSPENDED");
        assertThat(holdAmount(accountId)).isZero();
    }

    @Test
    @DisplayName("원래 SUSPENDED였던 계좌는 지급정지를 걸었다 풀어도 SUSPENDED로 남는다")
    void release_keepsOriginalSuspension() {
        long accountId = insertAccount(AccountStatus.SUSPENDED, 100_000L);

        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A));
        inTx(() -> paymentStopUseCase.convertToPartial(accountId, CASE_A, 30_000L));

        assertThat(status(accountId)).isEqualTo("SUSPENDED");
        assertThat(holdAmount(accountId)).isEqualTo(30_000L);

        inTx(() -> paymentStopUseCase.release(accountId, CASE_A));

        assertThat(status(accountId)).isEqualTo("SUSPENDED");
        assertThat(holdAmount(accountId)).isZero();
        assertThat(history(accountId)).isEmpty();
    }

    @Test
    @DisplayName("잔액이 요청액보다 적으면 다른 사건 보류액을 뺀 남은 잔액까지만 적용한다")
    void convertToPartial_capsAtRemainingBalance() {
        long accountId = insertAccount(AccountStatus.ACTIVE, 100_000L);
        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A));
        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_B));

        PartialStopResult first = inTx(() -> paymentStopUseCase.convertToPartial(accountId, CASE_A, 60_000L));
        PartialStopResult second = inTx(() -> paymentStopUseCase.convertToPartial(accountId, CASE_B, 70_000L));

        assertThat(first).isEqualTo(new PartialStopResult(60_000L, 60_000L));
        assertThat(second).isEqualTo(new PartialStopResult(70_000L, 40_000L));
        assertThat(holdAmount(accountId)).isEqualTo(100_000L);
        assertThat(status(accountId)).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("같은 계좌와 사건으로 다시 부르면 기록과 보류액이 늘지 않는다")
    void sameCall_isIdempotent() {
        long accountId = insertAccount(AccountStatus.ACTIVE, 100_000L);

        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A));
        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A));
        PartialStopResult first = inTx(() -> paymentStopUseCase.convertToPartial(accountId, CASE_A, 30_000L));
        PartialStopResult again = inTx(() -> paymentStopUseCase.convertToPartial(accountId, CASE_A, 30_000L));
        inTx(() -> paymentStopUseCase.release(accountId, CASE_A));
        inTx(() -> paymentStopUseCase.release(accountId, CASE_A));

        assertThat(again).isEqualTo(first);
        assertThat(stopCount(accountId)).isEqualTo(1);
        assertThat(status(accountId)).isEqualTo("ACTIVE");
        assertThat(holdAmount(accountId)).isZero();
        assertThat(history(accountId))
                .containsExactly("ACTIVE>SUSPENDED:PAYMENT_STOP", "SUSPENDED>ACTIVE:PAYMENT_STOP");
    }

    @ParameterizedTest
    @EnumSource(
            value = AccountStatus.class,
            names = {"MATURED", "DORMANT"})
    @DisplayName("ACTIVE, SUSPENDED 외 상태의 계좌에는 지급정지를 걸 수 없다")
    void applyFullStop_rejectsOtherStatus(AccountStatus status) {
        long accountId = insertAccount(status, 100_000L);

        assertThatThrownBy(() -> inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.INVALID_ACCOUNT_STATUS);
        assertThat(stopCount(accountId)).isZero();
    }

    @Test
    @DisplayName("기록이 없는 사건을 일부 지급정지로 바꾸거나 해제하면 거부한다")
    void unknownCase_isRejected() {
        long accountId = insertAccount(AccountStatus.ACTIVE, 100_000L);

        assertThatThrownBy(() -> inTx(() -> paymentStopUseCase.convertToPartial(accountId, CASE_A, 10_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.PAYMENT_STOP_NOT_FOUND);
        assertThatThrownBy(() -> inTx(() -> paymentStopUseCase.release(accountId, CASE_A)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.PAYMENT_STOP_NOT_FOUND);
    }

    @Test
    @DisplayName("일부 지급정지를 다른 요청액으로 다시 바꾸거나 해제한 사건에 다시 걸면 거부한다")
    void conflictingCall_isRejected() {
        long accountId = insertAccount(AccountStatus.ACTIVE, 100_000L);
        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A));
        inTx(() -> paymentStopUseCase.convertToPartial(accountId, CASE_A, 30_000L));

        assertThatThrownBy(() -> inTx(() -> paymentStopUseCase.convertToPartial(accountId, CASE_A, 40_000L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.PAYMENT_STOP_STATE_CONFLICT);

        inTx(() -> paymentStopUseCase.release(accountId, CASE_A));

        assertThatThrownBy(() -> inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AccountErrorCode.PAYMENT_STOP_STATE_CONFLICT);
    }

    @Test
    @DisplayName("다른 사건의 지급정지 이력이 있는지 해제된 기록까지 포함해 본다")
    void hasOtherStopHistory() {
        long accountId = insertAccount(AccountStatus.ACTIVE, 100_000L);
        inTx(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A));
        inTx(() -> paymentStopUseCase.release(accountId, CASE_A));

        assertThat(paymentStopUseCase.hasOtherStopHistory(accountId, CASE_B)).isTrue();
        assertThat(paymentStopUseCase.hasOtherStopHistory(accountId, CASE_A)).isFalse();
    }

    @Test
    @DisplayName("호출자 트랜잭션 없이 상태를 바꾸는 계약을 부르면 거부한다")
    void writeWithoutCallerTransaction_isRejected() {
        long accountId = insertAccount(AccountStatus.ACTIVE, 100_000L);

        assertThatThrownBy(() -> paymentStopUseCase.applyFullStop(accountId, CASE_A))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(status(accountId)).isEqualTo("ACTIVE");
    }

    private void inTx(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    private <T> T inTx(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    private long insertAccount(AccountStatus status, long balance) {
        String accountNumber = String.format("089%09d", customerId * 10L + accountSequence++);
        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_number, customer_id, account_type, balance, status,
                    password_hash, opened_date, created_at, updated_at
                ) VALUES (?, ?, 'DEMAND_DEPOSIT', ?, ?, ?, ?, ?, ?)
                """,
                accountNumber,
                customerId,
                balance,
                status.name(),
                PASSWORD_HASH,
                Timestamp.valueOf(OPENED_AT),
                Timestamp.valueOf(OPENED_AT),
                Timestamp.valueOf(OPENED_AT));
        return jdbcTemplate.queryForObject(
                "SELECT account_id FROM account WHERE account_number = ?", Long.class, accountNumber);
    }

    private String status(long accountId) {
        return jdbcTemplate.queryForObject("SELECT status FROM account WHERE account_id = ?", String.class, accountId);
    }

    private long holdAmount(long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT hold_amount FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private java.util.List<String> history(long accountId) {
        return jdbcTemplate.queryForList(
                """
                SELECT CONCAT(previous_status, '>', new_status, ':', trigger_type)
                FROM account_status_history
                WHERE account_id = ?
                ORDER BY history_id
                """,
                String.class,
                accountId);
    }

    private String stopRow(long accountId, long caseId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT CONCAT_WS(',', stop_type, requested_amount, applied_amount, status_before, active)
                FROM account_payment_stop
                WHERE account_id = ? AND case_id = ?
                """,
                String.class,
                accountId,
                caseId);
    }

    private boolean stopActive(long accountId, long caseId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT active FROM account_payment_stop WHERE account_id = ? AND case_id = ?",
                Boolean.class,
                accountId,
                caseId));
    }

    private int stopCount(long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_payment_stop WHERE account_id = ?", Integer.class, accountId);
    }
}
