package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferHold;
import com.shinhan.corebank.transfer.domain.TransferType;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보류 건 실행 경로(transfer_seam.md §9-4). 토큰 없이 sourceId=보류 ID로 들어온 즉시이체는 보류 행이 EXECUTING이고 원본과 같을 때만 실행된다.
 *
 * MockitoBean 구성은 TransferExecutionServiceTest와 같게 맞춰 컨텍스트 캐시를 공유한다. 검증기가 한 번도 불리지 않았으면 인증을 건너뛴 것이다.
 */
@DisplayName("보류 건 실행 경로 통합 테스트")
class TransferHoldExecutionTest extends IntegrationTestSupport {

    private static final LocalDateTime HELD_AT = LocalDateTime.of(2031, 7, 1, 10, 0);
    // 시드와 다른 테스트가 쓰지 않는 보류 ID 대역(없는 행 확인용)
    private static final long MISSING_HOLD_ID = 999_772_001L;

    @Autowired
    private TransferExecutionService transferExecutionService;

    @Autowired
    private TransferHoldPort transferHoldPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private OtpAuthTokenVerifier otpAuthTokenVerifier;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier accountPasswordAuthTokenVerifier;

    @BeforeEach
    void seed() {
        cleanUp();
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202");
        jdbcTemplate.update("UPDATE account SET balance = 100000, status = 'ACTIVE' WHERE account_id IN (101, 202)");
        jdbcTemplate.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = 1");
        jdbcTemplate.update("DELETE FROM transfer_hold WHERE customer_id = 1");
    }

    @Test
    @DisplayName("EXECUTING이고 원본과 같으면 토큰 없이 실행되고, 이체 행은 IMMEDIATE·source_type=HELD·보류한 날짜로 남는다")
    void executingHold_executesWithoutTokens() {
        long holdId = executingHold(30_000L);

        TransferResult result = transferExecutionService.execute(heldExecution(holdId, 30_000L));

        assertThat(result.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT transfer_type, source_type, source_id FROM transfer WHERE transaction_number = ?",
                result.transactionNumber());
        assertThat(row.get("transfer_type")).isEqualTo("IMMEDIATE");
        assertThat(row.get("source_type")).isEqualTo("HELD");
        assertThat(((Number) row.get("source_id")).longValue()).isEqualTo(holdId);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT execution_date FROM transfer WHERE transaction_number = ?",
                        LocalDate.class,
                        result.transactionNumber()))
                .isEqualTo(HELD_AT.toLocalDate());
        assertThat(balanceOf(101)).isEqualTo(70_000L);
        verifyNoInteractions(accountPasswordAuthTokenVerifier, otpAuthTokenVerifier);
    }

    @Test
    @DisplayName("같은 보류 건을 두 번 실행해도 기표는 1회고, 두 번째는 앞선 결과를 돌려받는다")
    void executingHold_executedTwice_postsOnce() {
        long holdId = executingHold(30_000L);

        TransferResult first = transferExecutionService.execute(heldExecution(holdId, 30_000L));
        TransferResult second = transferExecutionService.execute(heldExecution(holdId, 30_000L));

        assertThat(second.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(second.transactionNumber()).isEqualTo(first.transactionNumber());
        assertThat(balanceOf(101)).isEqualTo(70_000L);
        assertThat(ledgerRows()).isEqualTo(2);
    }

    @Test
    @DisplayName("보류 행이 없거나 HELD(실행 선점 전)면 인증 생략 없이 TRF0310으로 거부되고 이체 행이 생기지 않는다")
    void notExecutingHold_isRejected() {
        long heldOnly = transferHoldPort.save(hold(30_000L)).getTransferHoldId();

        assertRejected(transferExecutionService.execute(heldExecution(MISSING_HOLD_ID, 30_000L)));
        assertRejected(transferExecutionService.execute(heldExecution(heldOnly, 30_000L)));

        assertThat(transferRows()).isZero();
        assertThat(balanceOf(101)).isEqualTo(100_000L);
        verifyNoInteractions(accountPasswordAuthTokenVerifier, otpAuthTokenVerifier);
    }

    @Test
    @DisplayName("EXECUTING이어도 금액·수취계좌번호·출금계좌·고객이 원본과 다르면 TRF0310으로 거부된다")
    void executingHold_withDifferentRequest_isRejected() {
        long holdId = executingHold(30_000L);

        assertRejected(transferExecutionService.execute(heldExecution(holdId, 99_000L)));
        assertRejected(transferExecutionService.execute(heldExecutionBuilder(holdId, 30_000L)
                .depositAccountNumber("110999999999")
                .build()));
        assertRejected(transferExecutionService.execute(
                heldExecutionBuilder(holdId, 30_000L).withdrawalAccountId(202L).build()));
        assertRejected(transferExecutionService.execute(
                heldExecutionBuilder(holdId, 30_000L).customerId(2L).build()));

        assertThat(transferRows()).isZero();
    }

    private static void assertRejected(TransferResult result) {
        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.TRANSFER_HOLD_NOT_EXECUTABLE.getCode());
        assertThat(result.transactionNumber()).isNull();
    }

    private long executingHold(long amount) {
        long holdId = transferHoldPort.save(hold(amount)).getTransferHoldId();
        assertThat(transferHoldPort.markExecuting(holdId, null, HELD_AT.plusHours(3)))
                .isTrue();
        return holdId;
    }

    private static TransferHold hold(long amount) {
        return TransferHold.hold(
                1L,
                101L,
                "110222222222",
                amount,
                null,
                null,
                TransferChannel.WB,
                null,
                TransferHoldDecision.holdUntil("TEST", HELD_AT.plusHours(3)),
                HELD_AT,
                HELD_AT);
    }

    private static TransferCommand heldExecution(long holdId, long amount) {
        return heldExecutionBuilder(holdId, amount).build();
    }

    private static TransferCommand.TransferCommandBuilder heldExecutionBuilder(long holdId, long amount) {
        return TransferCommand.builder()
                .customerId(1L)
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(amount)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .sourceId(holdId)
                .executionDate(LocalDate.from(HELD_AT));
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private int ledgerRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (101, 202)", Integer.class);
    }

    private int transferRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer WHERE withdrawal_account_id IN (101, 202)", Integer.class);
    }
}
