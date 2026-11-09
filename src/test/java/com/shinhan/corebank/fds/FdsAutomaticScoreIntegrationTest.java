package com.shinhan.corebank.fds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.account.api.AccountPasswordAuthTokenVerifier;
import com.shinhan.corebank.business.support.BusinessDateTestFixture;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService;
import com.shinhan.corebank.fds.application.service.FdsHoldDetailService;
import com.shinhan.corebank.fds.application.service.FdsInvestigationInputService;
import com.shinhan.corebank.otp.api.OtpAuthTokenVerifier;
import com.shinhan.corebank.transfer.adapter.out.persistence.TransferTestFixtures;
import com.shinhan.corebank.transfer.application.port.in.TransferCommand;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferType;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 TransferHeld 커밋 이후 Modulith 리스너가 Python 점수를 저장하는지 확인한다. */
@TestPropertySource(properties = {"app.fds.policy.enabled=true", "app.fds.score.enabled=true"})
@EnabledIfEnvironmentVariable(named = "FDS_SCORE_SERVICE_TOKEN", matches = ".+")
class FdsAutomaticScoreIntegrationTest extends IntegrationTestSupport {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private BusinessDateTestFixture businessDate;

    @Autowired
    private BehaviorFlowService flows;

    @Autowired
    private TransferExecutionUseCase transfers;

    @Autowired
    private FdsHoldDetailService detail;

    @Autowired
    private FdsInvestigationInputService input;

    @MockitoBean
    private AccountPasswordAuthTokenVerifier passwordVerifier;

    @MockitoBean
    private OtpAuthTokenVerifier otpVerifier;

    private LocalDate originalDate;

    @AfterEach
    void cleanUp() {
        if (originalDate != null) businessDate.moveTo(originalDate);
        jdbc.update("DELETE FROM ledger_entry WHERE account_id IN (101, 202)");
        jdbc.update("DELETE FROM transfer WHERE withdrawal_account_id = 101 AND deposit_account_id = 202");
        jdbc.update("DELETE FROM transfer_limit_daily_usage WHERE customer_id = 1");
        jdbc.update("DELETE FROM transfer_hold WHERE customer_id = 1");
        jdbc.update("UPDATE account SET balance = 100000 WHERE account_id IN (101, 202)");
    }

    @Test
    void committedHeldEventAutomaticallyStoresPythonScore() {
        originalDate = businessDate.currentInDb();
        businessDate.moveTo(LocalDate.of(2026, 11, 2));
        new TransactionTemplate(transactions)
                .executeWithoutResult(status -> TransferTestFixtures.seedCustomerAndAccounts(entityManager));
        jdbc.update("UPDATE account SET balance = 3000000 WHERE account_id = 101");
        UUID flowId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.ofHours(9)).minusSeconds(100);
        flows.submit(
                flowId,
                1,
                new BehaviorFlowService.Batch(
                        List.of(
                                new BehaviorFlowService.EventInput(flowId, 1, "TRANSFER_ENTER", start, "D-01"),
                                new BehaviorFlowService.EventInput(
                                        flowId, 2, "SUBMIT_REQUESTED", start.plusSeconds(99), "D-02")),
                        65));
        for (int i = 0; i < 2; i++) {
            jdbc.update(
                    """
                    INSERT INTO transfer (transaction_number, withdrawal_account_id, deposit_account_id,
                                          deposit_account_number, payee_name, amount, fee,
                                          transfer_type, channel, status, transferred_at, created_at)
                    VALUES (?, 101, 202, '110222222222', '테스터', 1000000, 0,
                            'IMMEDIATE', 'WB', 'SUCCESS', ?, NOW(6))
                    """,
                    "20310601FDSE2E" + i,
                    LocalDateTime.now().minusMinutes(1));
        }
        var held = transfers.execute(TransferCommand.builder()
                .customerId(1L)
                .authToken("test-auth")
                .otpAuthToken("test-otp")
                .withdrawalAccountId(101L)
                .depositAccountNumber("110222222222")
                .amount(1_000_000L)
                .transferType(TransferType.IMMEDIATE)
                .channel(TransferChannel.WB)
                .flowId(flowId.toString())
                .build());
        assertThat(held.held()).isTrue();
        long holdId = held.transferHoldId();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                        "SELECT score_status FROM fds_behavior_score WHERE transfer_hold_id = ?", String.class, holdId))
                .isEqualTo("AVAILABLE"));
        var admin = detail.get(holdId, 1, "127.0.0.1", AccessReason.OPERATION);
        var agent = input.build(holdId, "inv-" + holdId);
        assertThat(admin.holdStatus()).isEqualTo("HELD");
        assertThat(agent.behaviorSummary().behaviorRiskScore()).isEqualTo(admin.behaviorRiskScore());
        assertThat(agent.behaviorSummary().modelVersion()).isEqualTo(admin.modelVersion());
        assertThat(agent.behaviorSummary().featureVersion()).isEqualTo(admin.featureVersion());
    }
}
