package com.shinhan.corebank.transfer.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.business.api.BusinessDateGate;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.api.TransferHoldDecision;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferHoldReleaseSummary;
import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.DelayedTransferSettingPort;
import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferAuthTokenVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferHoldPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLimitPort;
import com.shinhan.corebank.transfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.transfer.application.port.out.TransferOtpVerificationPort;
import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.application.port.out.TransferSequencePort;
import com.shinhan.corebank.transfer.config.DelayedTransferProperties;
import com.shinhan.corebank.transfer.domain.DelayedTransferSetting;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferHold;
import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import com.shinhan.corebank.transfer.domain.TransferType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 단계마다 따로 커밋하는 경로라 @Transactional을 두지 않고 직접 지운다. MockitoBean 구성은 다른 이체 통합 테스트와 같다. */
@DisplayName("지연이체 해제 배치 통합 테스트")
class TransferHoldReleaseBatchServiceTest extends IntegrationTestSupport {

    @Autowired
    private TransferHoldReleaseBatchService batchService;

    @Autowired
    private TransferHoldPort transferHoldPort;

    @Autowired
    private DelayedTransferSettingPort delayedTransferSettingPort;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

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
    private BusinessDateGate businessDateGate;

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
        jdbcTemplate.update("DELETE FROM delayed_transfer_setting WHERE customer_id = 1");
    }

    @Test
    @DisplayName("[완료 조건] 지연이체를 켠 고객의 기준금액 이상 이체는 보류되고, 해제 시각 전에는 실행되지 않다가 시각이 지나면 입금된다")
    void delayedTransfer_endToEnd() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> delayedTransferSettingPort.save(
                        DelayedTransferSetting.of(1L, true, 10_000L, 3), LocalDateTime.now(clock)));
        TransferExecutionService service = serviceWithDelayedTransferOn();

        TransferResult small = service.execute(immediate(9_999L));
        TransferResult held = service.execute(immediate(30_000L));

        assertThat(small.status()).as("기준금액 미만은 지금처럼 바로 실행").isEqualTo(ProcessResultStatus.SUCCESS);
        assertThat(held.held()).isTrue();
        assertThat(held.releaseAt()).isAfter(LocalDateTime.now(clock).plusHours(2));
        assertThat(balanceOf(101)).isEqualTo(100_000L - 9_999L);

        batchService.releaseDue();
        assertThat(holdStatus(held.transferHoldId())).isEqualTo("HELD");
        assertThat(balanceOf(101)).isEqualTo(100_000L - 9_999L);

        moveReleaseAtToPast(held.transferHoldId());
        TransferHoldReleaseSummary summary = batchService.releaseDue();

        assertThat(summary.released()).isEqualTo(1);
        assertThat(holdStatus(held.transferHoldId())).isEqualTo("EXECUTED");
        assertThat(balanceOf(101)).isEqualTo(100_000L - 9_999L - 30_000L);
        assertThat(balanceOf(202)).isEqualTo(100_000L + 9_999L + 30_000L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT source_type FROM transfer WHERE source_id = ? AND source_type = 'HELD'",
                        String.class,
                        held.transferHoldId()))
                .isEqualTo("HELD");
    }

    @Test
    @DisplayName("[완료 조건] 취소한 보류는 해제 시각이 지나도 실행되지 않는다")
    void cancelled_isNotReleased() {
        long holdId = atTime(LocalDateTime.now(clock).minusMinutes(1));
        transferHoldPort.close(holdId, TransferHoldStatus.CANCELLED, 1L, null, LocalDateTime.now(clock));

        batchService.releaseDue();

        assertThat(holdStatus(holdId)).isEqualTo("CANCELLED");
        assertThat(ledgerRows()).isZero();
    }

    @Test
    @DisplayName("승인으로 푸는 보류(FDS)는 해제 배치가 건드리지 않는다")
    void onApproval_isNotReleased() {
        long holdId = transferHoldPort
                .save(hold(TransferHoldDecision.holdForApproval("FDS_TEST"), 30_000L))
                .getTransferHoldId();

        batchService.releaseDue();

        assertThat(holdStatus(holdId)).isEqualTo("HELD");
        assertThat(ledgerRows()).isZero();
    }

    @Test
    @DisplayName("실행 중 멈춘 지 오래된 보류는 먼저 마무리하고, 최근에 선점된 건은 건드리지 않는다")
    void stuckExecuting_isResumed() {
        LocalDateTime now = LocalDateTime.now(clock);
        long stuck = atTime(now.minusHours(1));
        long recent = atTime(now.minusHours(1));
        transferHoldPort.markExecuting(stuck, null, now.minusMinutes(10));
        transferHoldPort.markExecuting(recent, null, now.minusMinutes(1));

        TransferHoldReleaseSummary summary = batchService.releaseDue();

        assertThat(summary.resumed()).isEqualTo(1);
        assertThat(holdStatus(stuck)).isEqualTo("EXECUTED");
        assertThat(holdStatus(recent)).isEqualTo("EXECUTING");
    }

    @Test
    @DisplayName("한 건이 실행 시점 검사에서 거부돼도 다음 건은 실행된다")
    void oneFailure_doesNotBlockOthers() {
        LocalDateTime now = LocalDateTime.now(clock);
        long tooBig = atTime(now.minusMinutes(2), 500_000L);
        long ok = atTime(now.minusMinutes(1), 30_000L);

        TransferHoldReleaseSummary summary = batchService.releaseDue();

        assertThat(summary.released()).isEqualTo(2);
        assertThat(holdStatus(tooBig)).isEqualTo("FAILED");
        assertThat(holdStatus(ok)).isEqualTo("EXECUTED");
        assertThat(balanceOf(101)).isEqualTo(70_000L);
    }

    private TransferExecutionService serviceWithDelayedTransferOn() {
        DelayedTransferHoldPolicy policy = new DelayedTransferHoldPolicy(
                new DelayedTransferProperties(true, Duration.ofMinutes(5), 100), delayedTransferSettingPort);
        return new TransferExecutionService(
                accountLockPort,
                transferLimitPort,
                transferAuthTokenVerificationPort,
                transferOtpVerificationPort,
                transferSequencePort,
                transferSavePort,
                transferLookupPort,
                ledgerSavePort,
                transferHoldPort,
                List.of(),
                List.of(),
                List.of(policy),
                clock,
                businessDateGate,
                transactionManager,
                eventPublisher,
                new SimpleMeterRegistry());
    }

    private long atTime(LocalDateTime releaseAt) {
        return atTime(releaseAt, 30_000L);
    }

    private long atTime(LocalDateTime releaseAt, long amount) {
        return transferHoldPort
                .save(hold(TransferHoldDecision.holdUntil("DELAYED_TRANSFER", releaseAt), amount))
                .getTransferHoldId();
    }

    private TransferHold hold(TransferHoldDecision decision, long amount) {
        LocalDateTime heldAt = LocalDateTime.now(clock).minusHours(3);
        return TransferHold.hold(
                1L, 101L, "110222222222", amount, null, null, TransferChannel.WB, null, decision, heldAt, heldAt);
    }

    private static TransferCommand immediate(long amount) {
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

    // 실제로 3시간을 기다릴 수 없어 해제 시각을 지난 것으로 옮긴다
    private void moveReleaseAtToPast(long holdId) {
        jdbcTemplate.update(
                "UPDATE transfer_hold SET release_at = ? WHERE transfer_hold_id = ?",
                LocalDateTime.now(clock).minusSeconds(1),
                holdId);
    }

    private String holdStatus(long holdId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM transfer_hold WHERE transfer_hold_id = ?", String.class, holdId);
    }

    private long balanceOf(long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }

    private int ledgerRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE account_id IN (101, 202)", Integer.class);
    }
}
