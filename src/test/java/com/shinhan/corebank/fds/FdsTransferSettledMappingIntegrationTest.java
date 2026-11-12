package com.shinhan.corebank.fds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.fds.api.BehaviorFlowMappingUseCase;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService;
import com.shinhan.corebank.transfer.api.TransferSettled;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 커밋 후 TransferSettled 재전달이 refId-flowId 매핑을 한 행으로 유지하는지 확인한다. */
class FdsTransferSettledMappingIntegrationTest extends IntegrationTestSupport {

    private static final long CUSTOMER_ID = 1L;

    @Autowired
    private BehaviorFlowService flows;

    @Autowired
    private BehaviorFlowMappingUseCase mappings;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID flowId;

    @AfterEach
    void cleanUp() {
        if (flowId == null) return;
        jdbc.update("DELETE FROM fds_transfer_behavior_flow WHERE flow_id = ?", flowId.toString());
        jdbc.update("DELETE FROM fds_behavior_event WHERE flow_id = ?", flowId.toString());
        jdbc.update("DELETE FROM fds_behavior_flow WHERE flow_id = ?", flowId.toString());
    }

    @Test
    void committedSuccessEventAndRedeliveryKeepOneMapping() {
        flowId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.ofHours(9)).minusSeconds(2);
        flows.submit(
                flowId,
                CUSTOMER_ID,
                new BehaviorFlowService.Batch(
                        List.of(
                                new BehaviorFlowService.EventInput(flowId, 1, "TRANSFER_ENTER", occurredAt, "D-01"),
                                new BehaviorFlowService.EventInput(
                                        flowId, 2, "SUBMIT_REQUESTED", occurredAt.plusSeconds(1), "D-02")),
                        null));
        long refId = 990_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000);
        LocalDateTime settledAt = LocalDateTime.now();
        TransferSettled event = new TransferSettled(
                CUSTOMER_ID,
                refId,
                "IMMEDIATE_TRANSFER",
                ProcessResultStatus.SUCCESS,
                null,
                settledAt,
                flowId.toString(),
                null);

        publishCommitted(event);
        publishCommitted(event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(mappings.findByRefId(refId).flowId()).isEqualTo(flowId.toString());
            assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM fds_transfer_behavior_flow WHERE ref_id = ?", Integer.class, refId))
                    .isEqualTo(1);
        });
    }

    private void publishCommitted(TransferSettled event) {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> events.publishEvent(event));
    }
}
