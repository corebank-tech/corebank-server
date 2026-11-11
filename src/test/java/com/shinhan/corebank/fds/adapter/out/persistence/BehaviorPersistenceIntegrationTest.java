package com.shinhan.corebank.fds.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.fds.api.BehaviorFlowMappingUseCase;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorScoreInferencePort.Score;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService.Batch;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService.EventInput;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** MySQL에서 행동 이벤트의 원자성과 점수 상태 전이를 확인한다. */
class BehaviorPersistenceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private BehaviorFlowService flowService;

    @Autowired
    private BehaviorFlowPort flows;

    @Autowired
    private BehaviorScorePort scores;

    @Autowired
    private BehaviorFlowMappingUseCase mappings;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void finalBatchIsAtomicAndReplayDoesNotDuplicateEvents() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(9)).minusSeconds(10);
        Batch batch = new Batch(
                List.of(
                        new EventInput(id, 1, "TRANSFER_ENTER", now, "D-01"),
                        new EventInput(id, 2, "CONFIRM_ENTER", now.plusSeconds(1), "D-02"),
                        new EventInput(id, 3, "CONFIRM_LEAVE", now.plusSeconds(5), "D-02"),
                        new EventInput(id, 4, "SUBMIT_REQUESTED", now.plusSeconds(6), "D-02")),
                4);

        assertThat(flowService.submit(id, 951001, batch).acceptedCount()).isEqualTo(4);
        assertThat(flowService.submit(id, 951001, batch).acceptedCount()).isZero();
        assertThat(flows.events(id.toString())).hasSize(4);
        assertThat(flows.find(id.toString()).confirmActiveSeconds()).isEqualTo(4);
        assertThat(flows.find(id.toString()).finalizedAt()).isNotNull();
    }

    @Test
    void scoreTransitionsOnlyOnceAndPersistsVersions() {
        long id = 951000000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000000);
        LocalDateTime now = LocalDateTime.now();
        scores.createIfAbsent(id, null, now);
        scores.createIfAbsent(id, null, now);
        assertThat(scores.markStarted(id, now)).isTrue();
        assertThat(scores.markStarted(id, now)).isFalse();
        assertThat(scores.available(id, new Score(0.81, "xgb-demo-v1", "p6-behavior-v1"), now))
                .isTrue();
        assertThat(scores.unavailable(id, "INFERENCE_TIMEOUT", now)).isFalse();
        assertThat(scores.find(id).status()).isEqualTo("AVAILABLE");
        assertThat(scores.find(id).behaviorRiskScore()).isEqualTo(0.81);
        assertThat(scores.find(id).modelVersion()).isEqualTo("xgb-demo-v1");
    }

    @Test
    void concurrentWorkersClaimOneAttemptAndReplayCannotOverwriteUnavailable() throws Exception {
        long id = 951000000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000000);
        LocalDateTime now = LocalDateTime.now();
        scores.createIfAbsent(id, null, now);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await();
                return scores.markStarted(id, now);
            });
            var second = workers.submit(() -> {
                start.await();
                return scores.markStarted(id, now);
            });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(scores.unavailable(id, "SCORING_INTERRUPTED", now)).isTrue();
        scores.createIfAbsent(id, null, now);
        assertThat(scores.markStarted(id, now)).isFalse();
        assertThat(scores.available(id, new Score(0.81, "late-model", "p6-behavior-v1"), now))
                .isFalse();
        assertThat(scores.find(id).status()).isEqualTo("UNAVAILABLE");
        assertThat(scores.find(id).unavailableReason()).isEqualTo("SCORING_INTERRUPTED");
        assertThat(scores.find(id).behaviorRiskScore()).isNull();
    }

    @Test
    void mappingIsIdempotentAndDifferentFlowCannotOverwriteExistingRef() {
        long customerId = 975001L;
        long refId = positiveRefId();
        UUID firstFlow = createFlow(customerId);
        UUID secondFlow = createFlow(customerId);

        mappings.save(refId, firstFlow.toString(), customerId);
        mappings.save(refId, firstFlow.toString(), customerId);
        assertThatThrownBy(() -> mappings.save(refId, secondFlow.toString(), customerId))
                .isInstanceOf(com.shinhan.corebank.common.exception.BusinessException.class);

        assertThat(mappings.findByRefId(refId).flowId()).isEqualTo(firstFlow.toString());
        assertThat(mappings.findByFlowId(firstFlow.toString()).refId()).isEqualTo(refId);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_transfer_behavior_flow WHERE ref_id = ?", Integer.class, refId))
                .isEqualTo(1);
    }

    @Test
    void concurrentSameMappingKeepsOneDatabaseRow() throws Exception {
        long customerId = 975001L;
        long refId = positiveRefId();
        UUID flowId = createFlow(customerId);
        CountDownLatch start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await();
                return mappings.save(refId, flowId.toString(), customerId);
            });
            var second = workers.submit(() -> {
                start.await();
                return mappings.save(refId, flowId.toString(), customerId);
            });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).flowId()).isEqualTo(flowId.toString());
            assertThat(second.get(10, TimeUnit.SECONDS).flowId()).isEqualTo(flowId.toString());
        }

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_transfer_behavior_flow WHERE ref_id = ?", Integer.class, refId))
                .isEqualTo(1);
    }

    @Test
    void concurrentDifferentFlowsForSameRefKeepFirstWinner() throws Exception {
        long customerId = 975001L;
        long refId = positiveRefId();
        UUID firstFlow = createFlow(customerId);
        UUID secondFlow = createFlow(customerId);
        CountDownLatch start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> saveAfter(start, refId, firstFlow, customerId));
            var second = workers.submit(() -> saveAfter(start, refId, secondFlow, customerId));
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        String storedFlow = mappings.findByRefId(refId).flowId();
        assertThat(storedFlow).isIn(firstFlow.toString(), secondFlow.toString());
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_transfer_behavior_flow WHERE ref_id = ?", Integer.class, refId))
                .isEqualTo(1);
    }

    @Test
    void concurrentIdenticalEventSubmissionKeepsOneEvent() throws Exception {
        long customerId = 975001L;
        UUID flowId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.ofHours(9)).minusSeconds(1);
        Batch batch = new Batch(List.of(new EventInput(flowId, 1, "TRANSFER_ENTER", occurredAt, "D-01")), null);
        CountDownLatch start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await();
                return flowService.submit(flowId, customerId, batch).acceptedCount();
            });
            var second = workers.submit(() -> {
                start.await();
                return flowService.submit(flowId, customerId, batch).acceptedCount();
            });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1, 0);
        }

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_behavior_event WHERE flow_id = ?", Integer.class, flowId.toString()))
                .isEqualTo(1);
    }

    private UUID createFlow(long customerId) {
        UUID flowId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.ofHours(9)).minusSeconds(1);
        flowService.submit(
                flowId,
                customerId,
                new Batch(List.of(new EventInput(flowId, 1, "TRANSFER_ENTER", occurredAt, "D-01")), null));
        return flowId;
    }

    private static long positiveRefId() {
        return 975000000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000000);
    }

    private boolean saveAfter(CountDownLatch start, long refId, UUID flowId, long customerId) throws Exception {
        start.await();
        try {
            mappings.save(refId, flowId.toString(), customerId);
            return true;
        } catch (com.shinhan.corebank.common.exception.BusinessException exception) {
            return false;
        }
    }
}
