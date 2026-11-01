package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.api.TransferHoldExecutionResult;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferHold;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 단계마다 따로 커밋하는 서비스라 @Transactional을 두지 않고 직접 지운다. MockitoBean 구성은 다른 이체 통합 테스트와 같다. */
@DisplayName("이체 보류 실행·거절·취소(TransferHoldService) 통합 테스트")
class TransferHoldServiceIntegrationTest extends IntegrationTestSupport {

    private static final LocalDateTime HELD_AT = LocalDateTime.of(2031, 7, 1, 10, 0);
    private static final long CUSTOMER_ID = 1L;
    private static final long OTHER_CUSTOMER_ID = 2L;
    private static final long ADMIN_ID = 9_772L;
    private static final long MISSING_HOLD_ID = 999_772_002L;

    @Autowired
    private TransferHoldService transferHoldService;

    @Autowired
    private TransferHoldPort transferHoldPort;

    @Autowired
    private TransferExecutionUseCase transferExecutionUseCase;

    @Autowired
    private TransferLookupPort transferLookupPort;

    @Autowired
    private Clock clock;

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
    @DisplayName("실행하면 원본 그대로 기표하고 EXECUTED·transfer_id·실행 주체를 남긴다. 다시 실행하면 앞선 결과를 돌려준다")
    void release_executesOnce_andReplays() {
        long holdId = held(30_000L);

        TransferHoldExecutionResult first = transferHoldService.release(holdId, ADMIN_ID);
        TransferHoldExecutionResult second = transferHoldService.release(holdId, ADMIN_ID);

        assertThat(first.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(second).isEqualTo(first);
        Map<String, Object> hold = holdRow(holdId);
        assertThat(hold.get("status")).isEqualTo("EXECUTED");
        assertThat(((Number) hold.get("actor_id")).longValue()).isEqualTo(ADMIN_ID);
        assertThat(((Number) hold.get("transfer_id")).longValue()).isEqualTo(transferIdOf(first.transactionNumber()));
        assertThat(balanceOf(101)).isEqualTo(70_000L);
        assertThat(ledgerRows()).isEqualTo(2);
    }

    @Test
    @DisplayName("실행 시점에 잔액이 모자라면 훅 A가 거부해 FAILED로 끝나고 원장은 그대로다. 다시 물어도 같은 실패를 돌려준다")
    void release_insufficientBalanceAtExecution_fails() {
        long holdId = held(30_000L);
        jdbcTemplate.update("UPDATE account SET balance = 1000 WHERE account_id = 101");

        TransferHoldExecutionResult result = transferHoldService.releaseAsSystem(holdId);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        // 실행 시점에도 훅 A(PH-90 출금가능액)가 락 전에 먼저 거부한다
        assertThat(result.errorCode()).isEqualTo("LMT0001");
        assertThat(holdRow(holdId).get("status")).isEqualTo("FAILED");
        assertThat(holdRow(holdId).get("actor_id")).isNull();
        assertThat(ledgerRows()).isZero();
        assertThat(transferHoldService.releaseAsSystem(holdId)).isEqualTo(result);
    }

    @Test
    @DisplayName("실행 시점에 출금계좌가 정지됐으면 FAILED이고 기표되지 않는다")
    void release_suspendedAccountAtExecution_fails() {
        long holdId = held(30_000L);
        jdbcTemplate.update("UPDATE account SET status = 'SUSPENDED' WHERE account_id = 101");

        TransferHoldExecutionResult result = transferHoldService.releaseAsSystem(holdId);

        assertThat(result.status()).isEqualTo(ProcessResultStatus.ERROR);
        assertThat(result.errorCode()).isEqualTo(TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED.getCode());
        assertThat(holdRow(holdId).get("status")).isEqualTo("FAILED");
        assertThat(holdRow(holdId).get("reason_code"))
                .isEqualTo(TransferErrorCode.WITHDRAWAL_ACCOUNT_SUSPENDED.getCode());
        assertThat(ledgerRows()).isZero();
    }

    @Test
    @DisplayName("같은 보류를 여러 곳에서 동시에 실행해도 기표는 1회다")
    void release_concurrently_postsOnce() throws Exception {
        long holdId = held(30_000L);
        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferHoldExecutionResult>> futures = new ArrayList<>();
        try {
            Callable<TransferHoldExecutionResult> attempt = () -> {
                start.await();
                return transferHoldService.releaseAsSystem(holdId);
            };
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(attempt));
            }
            start.countDown();

            int succeeded = 0;
            for (Future<TransferHoldExecutionResult> future : futures) {
                try {
                    if (future.get(30, TimeUnit.SECONDS).status() == ProcessResultStatus.SUCCESS) {
                        succeeded++;
                    }
                } catch (ExecutionException e) {
                    // 앞선 호출이 아직 실행 중이면 TRF0311로 거부된다
                    assertThat(e.getCause()).isInstanceOf(BusinessException.class);
                }
            }
            assertThat(succeeded).isPositive();
        } finally {
            pool.shutdownNow();
        }
        assertThat(ledgerRows()).isEqualTo(2);
        assertThat(balanceOf(101)).isEqualTo(70_000L);
        assertThat(holdRow(holdId).get("status")).isEqualTo("EXECUTED");
    }

    @Test
    @DisplayName("고객이 취소한 보류는 실행하면 TRF0311로 거부되고 기표되지 않는다")
    void cancel_thenRelease_isRejected() {
        long holdId = held(30_000L);

        transferHoldService.cancel(holdId, CUSTOMER_ID);

        assertThat(holdRow(holdId).get("status")).isEqualTo("CANCELLED");
        assertBusinessError(
                () -> transferHoldService.releaseAsSystem(holdId), TransferErrorCode.TRANSFER_HOLD_ALREADY_CLOSED);
        assertBusinessError(
                () -> transferHoldService.cancel(holdId, CUSTOMER_ID), TransferErrorCode.TRANSFER_HOLD_ALREADY_CLOSED);
        assertThat(ledgerRows()).isZero();
    }

    @Test
    @DisplayName("남의 보류와 없는 보류는 똑같이 TRF0203이고, 남의 보류는 그대로 HELD다")
    void cancel_othersOrMissing_isNotFound() {
        long holdId = held(30_000L);

        assertBusinessError(
                () -> transferHoldService.cancel(holdId, OTHER_CUSTOMER_ID), TransferErrorCode.TRANSFER_HOLD_NOT_FOUND);
        assertBusinessError(
                () -> transferHoldService.cancel(MISSING_HOLD_ID, CUSTOMER_ID),
                TransferErrorCode.TRANSFER_HOLD_NOT_FOUND);
        assertThat(holdRow(holdId).get("status")).isEqualTo("HELD");
    }

    @Test
    @DisplayName("관리자가 거절하면 REJECTED와 사유가 남고, 다시 거절하거나 실행하면 TRF0311이다")
    void reject_closesHold() {
        long holdId = held(30_000L);

        transferHoldService.reject(holdId, ADMIN_ID, "FDS_CONFIRMED_FRAUD");

        Map<String, Object> hold = holdRow(holdId);
        assertThat(hold.get("status")).isEqualTo("REJECTED");
        assertThat(hold.get("reason_code")).isEqualTo("FDS_CONFIRMED_FRAUD");
        assertBusinessError(
                () -> transferHoldService.reject(holdId, ADMIN_ID, "AGAIN"),
                TransferErrorCode.TRANSFER_HOLD_ALREADY_CLOSED);
        assertBusinessError(
                () -> transferHoldService.release(holdId, ADMIN_ID), TransferErrorCode.TRANSFER_HOLD_ALREADY_CLOSED);
        assertBusinessError(
                () -> transferHoldService.reject(MISSING_HOLD_ID, ADMIN_ID, "X"),
                TransferErrorCode.TRANSFER_HOLD_NOT_FOUND);
    }

    @Test
    @DisplayName("실행 중 예외로 멈추면 EXECUTING으로 남고, resume()이 다시 실행해 EXECUTED로 마무리한다")
    void release_throws_leavesExecuting_resumeFinishes() {
        long holdId = held(30_000L);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        TransferExecutionUseCase flaky = command -> {
            if (failOnce.getAndSet(false)) {
                throw new IllegalStateException("crashed");
            }
            return transferExecutionUseCase.execute(command);
        };
        TransferHoldService service = new TransferHoldService(transferHoldPort, flaky, transferLookupPort, clock);

        assertThatThrownBy(() -> service.releaseAsSystem(holdId)).isInstanceOf(IllegalStateException.class);
        assertThat(holdRow(holdId).get("status")).isEqualTo("EXECUTING");
        assertBusinessError(() -> service.releaseAsSystem(holdId), TransferErrorCode.TRANSFER_HOLD_ALREADY_CLOSED);

        TransferHoldExecutionResult resumed = service.resume(holdId);

        assertThat(resumed.status()).isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(holdRow(holdId).get("status")).isEqualTo("EXECUTED");
        assertThat(ledgerRows()).isEqualTo(2);
    }

    private static void assertBusinessError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call, TransferErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(expected));
    }

    private long held(long amount) {
        return transferHoldPort
                .save(TransferHold.hold(
                        CUSTOMER_ID,
                        101L,
                        "110222222222",
                        amount,
                        null,
                        null,
                        TransferChannel.WB,
                        null,
                        TransferHoldDecision.holdUntil("TEST", HELD_AT.plusHours(3)),
                        HELD_AT,
                        HELD_AT))
                .getTransferHoldId();
    }

    private Map<String, Object> holdRow(long holdId) {
        return jdbcTemplate.queryForMap("SELECT * FROM transfer_hold WHERE transfer_hold_id = ?", holdId);
    }

    private long transferIdOf(String transactionNumber) {
        return jdbcTemplate.queryForObject(
                "SELECT transfer_id FROM transfer WHERE transaction_number = ?", Long.class, transactionNumber);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private int ledgerRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (101, 202)", Integer.class);
    }
}
