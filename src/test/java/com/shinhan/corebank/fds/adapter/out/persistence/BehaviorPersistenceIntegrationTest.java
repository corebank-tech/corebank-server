package com.shinhan.corebank.fds.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shinhan.corebank.IntegrationTestSupport;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.fds.api.BehaviorFlowMappingUseCase;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorScoreInferencePort.Score;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import com.shinhan.corebank.fds.application.port.out.FdsAdminReadPort;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort.HistorySnapshotItem;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService.Batch;
import com.shinhan.corebank.fds.application.service.BehaviorFlowService.EventInput;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
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
    private FdsInvestigationJobPort investigationJobs;

    @Autowired
    private FdsAdminReadPort adminReads;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void investigationJobKeepsStableIdAndCompletesWithOneReport() {
        long holdId = 992000000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000000);
        String firstId = UUID.randomUUID().toString();
        String replayId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        assertThat(investigationJobs.createIfAbsent(holdId, firstId, now).investigationId())
                .isEqualTo(firstId);
        assertThat(investigationJobs.createIfAbsent(holdId, replayId, now).investigationId())
                .isEqualTo(firstId);
        assertThat(investigationJobs.claim(holdId, now, now.plusSeconds(70))).isEqualTo(1);
        assertThat(investigationJobs.claim(holdId, now, now.plusSeconds(70))).isNull();
        investigationJobs.recordEvidence(firstId, holdId, 992001L, now.minusHours(24), now, List.of(101L), now);
        investigationJobs.recordEvidence(firstId, holdId, 992001L, now.minusHours(24), now, List.of(101L), now);
        assertThatThrownBy(() -> investigationJobs.recordRecipientSnapshot(
                        firstId, holdId, 2, 992001L, now.minusDays(1), true, 2, now))
                .isInstanceOf(BusinessException.class);
        investigationJobs.recordHistorySnapshot(
                firstId,
                holdId,
                1,
                992001L,
                now.minusHours(24),
                now,
                true,
                List.of(new HistorySnapshotItem(101L, 500_000L, now.minusMinutes(1), "***-**-0000")),
                now);
        investigationJobs.recordRecipientSnapshot(firstId, holdId, 1, 992001L, now.minusDays(1), true, 2, now);

        investigationJobs.completeWithReport(
                holdId, firstId, 1, "p6-investigation-v1", "{\"result\":\"ok\"}", "a".repeat(64), now);
        investigationJobs.completeWithReport(
                holdId, firstId, 1, "p6-investigation-v1", "{\"result\":\"ok\"}", "a".repeat(64), now);

        assertThatThrownBy(() -> investigationJobs.completeWithReport(
                        holdId, firstId, 1, "p6-investigation-v1", "{\"result\":\"different\"}", "b".repeat(64), now))
                .isInstanceOf(BusinessException.class);

        assertThat(investigationJobs.findByHoldId(holdId).status()).isEqualTo("COMPLETED");
        assertThat(investigationJobs.findEvidenceIds(firstId)).containsExactly(101L);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_investigation_report WHERE investigation_id=?",
                        Integer.class,
                        firstId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT report_sha256 FROM fds_investigation_report WHERE investigation_id=?",
                        String.class,
                        firstId))
                .isEqualTo("a".repeat(64));
        assertThat(adminReads.findToolResults(firstId).history().hasMore()).isTrue();
        assertThat(adminReads.findToolResults(firstId).history().items()).hasSize(1);
        assertThat(adminReads.findToolResults(firstId).recipient().otherWithdrawalAccountCount())
                .isEqualTo(2);
        assertThat(adminReads.findReport(firstId).schemaVersion()).isEqualTo("p6-investigation-v1");
        assertThat(adminReads.findHoldIds()).contains(holdId);
    }

    @Test
    void concurrentInvestigationRegistrationKeepsOneStableId() throws Exception {
        long holdId = 992000000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000000);
        String firstId = UUID.randomUUID().toString();
        String secondId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        CountDownLatch start = new CountDownLatch(1);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                start.await();
                return investigationJobs.createIfAbsent(holdId, firstId, now).investigationId();
            });
            var second = workers.submit(() -> {
                start.await();
                return investigationJobs.createIfAbsent(holdId, secondId, now).investigationId();
            });
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        }

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_investigation_job WHERE transfer_hold_id=?", Integer.class, holdId))
                .isEqualTo(1);
    }

    @Test
    void investigationRetryFailureAndExpiredLeaseRecoveryPersistTransitions() {
        LocalDateTime now = LocalDateTime.now();
        long retryHoldId = investigationHoldId();
        String retryId = UUID.randomUUID().toString();
        investigationJobs.createIfAbsent(retryHoldId, retryId, now);
        assertThat(investigationJobs.findDue(now, 100)).contains(retryHoldId);
        assertThat(investigationJobs.claim(retryHoldId, now, now.plusSeconds(70)))
                .isEqualTo(1);
        assertThat(investigationJobs.retry(retryHoldId, 1, now.plusMinutes(1), "AGENT_UNAVAILABLE", now))
                .isTrue();
        assertThat(investigationJobs.findByInvestigationId(retryId).status()).isEqualTo("PENDING");
        assertThat(investigationJobs.findDue(now, 100)).doesNotContain(retryHoldId);
        assertThat(investigationJobs.findDue(now.plusMinutes(1), 100)).contains(retryHoldId);

        assertThat(investigationJobs.claim(retryHoldId, now.plusMinutes(1), now.plusMinutes(2)))
                .isEqualTo(2);
        assertThat(investigationJobs.retry(retryHoldId, 1, now.plusMinutes(2), "STALE_RETRY", now))
                .isFalse();
        assertThat(investigationJobs.fail(retryHoldId, 1, "STALE_FAILURE", now)).isFalse();
        assertThatThrownBy(() -> investigationJobs.completeWithReport(
                        retryHoldId, retryId, 1, "p6-investigation-v1", "{\"result\":\"stale\"}", "d".repeat(64), now))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_investigation_report WHERE investigation_id=?",
                        Integer.class,
                        retryId))
                .isZero();
        assertThat(investigationJobs.fail(retryHoldId, 2, "FINAL_FAILURE", now.plusMinutes(1)))
                .isTrue();
        assertThat(investigationJobs.findByHoldId(retryHoldId).status()).isEqualTo("FAILED");
        assertThat(investigationJobs.claim(retryHoldId, now.plusMinutes(2), now.plusMinutes(3)))
                .isNull();

        long pendingRecoveryHoldId = investigationHoldId();
        long failedRecoveryHoldId = investigationHoldId();
        investigationJobs.createIfAbsent(
                pendingRecoveryHoldId, UUID.randomUUID().toString(), now);
        investigationJobs.createIfAbsent(failedRecoveryHoldId, UUID.randomUUID().toString(), now);
        jdbc.update(
                "UPDATE fds_investigation_job SET job_status='RUNNING', attempt_count=1, lease_expires_at=? "
                        + "WHERE transfer_hold_id=?",
                now.minusSeconds(1),
                pendingRecoveryHoldId);
        jdbc.update(
                "UPDATE fds_investigation_job SET job_status='RUNNING', attempt_count=3, lease_expires_at=? "
                        + "WHERE transfer_hold_id=?",
                now.minusSeconds(1),
                failedRecoveryHoldId);

        assertThat(investigationJobs.recoverExpired(now)).isGreaterThanOrEqualTo(2);
        assertThat(investigationJobs.findByHoldId(pendingRecoveryHoldId).status())
                .isEqualTo("PENDING");
        assertThat(investigationJobs.findByHoldId(pendingRecoveryHoldId).lastFailureReason())
                .isEqualTo("LEASE_EXPIRED");
        assertThat(investigationJobs.findByHoldId(failedRecoveryHoldId).status())
                .isEqualTo("FAILED");
    }

    @Test
    void investigationEvidenceRejectsInvalidScopeAndUnknownJob() {
        LocalDateTime now = LocalDateTime.now();
        long holdId = investigationHoldId();
        String investigationId = UUID.randomUUID().toString();
        investigationJobs.createIfAbsent(holdId, investigationId, now);

        assertThatThrownBy(() ->
                        investigationJobs.recordEvidence(investigationId, holdId, 992001L, null, now, List.of(1L), now))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() ->
                        investigationJobs.recordEvidence(investigationId, holdId, 992001L, now, now, List.of(1L), now))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> investigationJobs.recordEvidence(
                        investigationId, holdId, 992001L, now.minusHours(1), now, List.of(0L), now))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> investigationJobs.recordEvidence(
                        UUID.randomUUID().toString(), holdId, 992001L, now.minusHours(1), now, List.of(1L), now))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> investigationJobs.recordEvidence(
                        investigationId, holdId + 1, 992001L, now.minusHours(1), now, List.of(1L), now))
                .isInstanceOf(BusinessException.class);
        assertThat(investigationJobs.findByHoldId(Long.MAX_VALUE)).isNull();
        assertThat(investigationJobs.findByInvestigationId(UUID.randomUUID().toString()))
                .isNull();
    }

    @Test
    void investigationReportCannotCompletePendingJob() {
        LocalDateTime now = LocalDateTime.now();
        long holdId = investigationHoldId();
        String investigationId = UUID.randomUUID().toString();
        investigationJobs.createIfAbsent(holdId, investigationId, now);

        assertThatThrownBy(() -> investigationJobs.completeWithReport(
                        holdId, investigationId, 0, "p6-investigation-v1", "{\"result\":\"ok\"}", "c".repeat(64), now))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_investigation_report WHERE investigation_id=?",
                        Integer.class,
                        investigationId))
                .isZero();
    }

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
    void differentRefCannotReuseFlowAlreadyLinkedToAnotherTransfer() {
        long customerId = 975001L;
        long firstRefId = positiveRefId();
        long secondRefId = firstRefId + 1;
        UUID flowId = createFlow(customerId);

        mappings.save(firstRefId, flowId.toString(), customerId);

        assertThatThrownBy(() -> mappings.save(secondRefId, flowId.toString(), customerId))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(FdsErrorCode.FLOW_CONFLICT);
        assertThat(mappings.findByFlowId(flowId.toString()).refId()).isEqualTo(firstRefId);
        assertThat(mappings.findByRefId(secondRefId)).isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM fds_transfer_behavior_flow WHERE flow_id = ?",
                        Integer.class,
                        flowId.toString()))
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
        assertThat(mappings.findByFlowId(storedFlow).refId()).isEqualTo(refId);
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

    private static long investigationHoldId() {
        return 992000000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1000000);
    }

    private boolean saveAfter(CountDownLatch start, long refId, UUID flowId, long customerId) throws Exception {
        start.await();
        try {
            mappings.save(refId, flowId.toString(), customerId);
            return true;
        } catch (BusinessException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(FdsErrorCode.FLOW_CONFLICT);
            return false;
        }
    }
}
