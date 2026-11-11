package com.shinhan.corebank.fds.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.IntegrationTestSupport;
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

/** MySQL에서 행동 이벤트의 원자성과 점수 상태 전이를 확인한다. */
class BehaviorPersistenceIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private BehaviorFlowService flowService;

    @Autowired
    private BehaviorFlowPort flows;

    @Autowired
    private BehaviorScorePort scores;

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
}
