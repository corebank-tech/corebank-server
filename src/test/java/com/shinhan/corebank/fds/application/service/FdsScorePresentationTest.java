package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.audit.PersonalDataAccessEventType;
import com.shinhan.corebank.fds.adapter.in.web.FdsAdminHoldController;
import com.shinhan.corebank.fds.api.BehaviorScoreView;
import com.shinhan.corebank.fds.api.FdsHoldDetail;
import com.shinhan.corebank.fds.api.FdsInvestigationInput;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorScoreInferencePort;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import com.shinhan.corebank.transfer.api.TransferHoldExecutionResult;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 관리자 상세와 조사 입력이 P6 저장 점수의 상태별 공개 규칙을 지키는지 검증한다. */
class FdsScorePresentationTest {
    private final ScoreStub scores = new ScoreStub();
    private final AuditStub audit = new AuditStub();
    private final TransferHoldUseCase holds = new HoldStub();
    private final FdsHoldDetailService detail = new FdsHoldDetailService(holds, scores, audit);
    private final FdsInvestigationInputService input = new FdsInvestigationInputService(holds, new FlowStub(), scores);

    @Test
    void availableScoreComesOnlyFromStoredResult() {
        scores.view = new BehaviorScoreView(123, "AVAILABLE", 0.81, "xgb-demo-v1", "p6-behavior-v1", null);
        FdsHoldDetail admin = detail.get(123, 9, "127.0.0.1", AccessReason.OPERATION);
        FdsInvestigationInput agent = input.build(123, "inv-123");
        assertThat(admin.behaviorScoreStatus()).isEqualTo("AVAILABLE");
        assertThat(admin.behaviorRiskScore()).isEqualTo(0.81);
        assertThat(admin.modelVersion()).isEqualTo("xgb-demo-v1");
        assertThat(admin.featureVersion()).isEqualTo("p6-behavior-v1");
        assertThat(admin.behaviorScoreUnavailableReason()).isNull();
        assertThat(admin.recipientAccountMasked()).isEqualTo("08******67");
        assertThat(admin.serverPolicy().policyCode()).isEqualTo("A");
        assertThat(admin.serverPolicy().count10m()).isEqualTo(3);
        assertThat(admin.serverPolicy().amount10m()).isEqualTo(1_500_000);
        assertThat(admin.serverPolicy().hesitationInWindow()).isTrue();
        assertThat(admin.serverPolicy().newRecipientAtWindowStart()).isTrue();
        assertThat(agent.behaviorSummary().behaviorRiskScore()).isEqualTo(admin.behaviorRiskScore());
        assertThat(agent.behaviorSummary().modelVersion()).isEqualTo(admin.modelVersion());
        assertThat(agent.behaviorSummary().featureVersion()).isEqualTo(admin.featureVersion());
        assertThat(agent.transferSummary().recipientAccountMasked()).isEqualTo("***-**-4567");
        assertThat(audit.lastHoldId).isEqualTo(123);
    }

    @Test
    void controllerReturnsStoredScoreInSuccessEnvelopeWithAdminAuditContext() {
        scores.view = new BehaviorScoreView(123, "AVAILABLE", 0.81, "xgb-demo-v1", "p6-behavior-v1", null);
        CurrentCustomerProvider currentCustomer = new CurrentCustomerProvider() {
            @Override
            public AuthenticatedCustomer getCurrentCustomer() {
                throw new AssertionError();
            }

            @Override
            public Long getCurrentCustomerId() {
                return 9L;
            }
        };
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        var response = new FdsAdminHoldController(detail, currentCustomer).get(123, AccessReason.OPERATION, request);

        assertThat(response.code()).isEqualTo("0000");
        assertThat(response.data().behaviorRiskScore()).isEqualTo(0.81);
        assertThat(audit.lastAdminId).isEqualTo(9L);
        assertThat(audit.lastIp).isEqualTo("127.0.0.1");
    }

    @Test
    void pendingScoreHasNoValue() {
        scores.view = new BehaviorScoreView(123, "PENDING", null, null, null, null);
        assertNoScore("PENDING", null);
    }

    @Test
    void absentScoreDoesNotBlockInput() {
        assertNoScore("PENDING", null);
    }

    @Test
    void unavailableScoreHasReasonButNoValue() {
        scores.view = new BehaviorScoreView(123, "UNAVAILABLE", null, null, null, "MODEL_UNAVAILABLE");
        assertNoScore("UNAVAILABLE", "MODEL_UNAVAILABLE");
    }

    private void assertNoScore(String status, String reason) {
        FdsHoldDetail admin = detail.get(123, 9, "127.0.0.1", AccessReason.OPERATION);
        FdsInvestigationInput agent = input.build(123, "inv-123");
        assertThat(admin.behaviorScoreStatus()).isEqualTo(status);
        assertThat(admin.behaviorRiskScore()).isNull();
        assertThat(admin.modelVersion()).isNull();
        assertThat(admin.featureVersion()).isNull();
        assertThat(admin.behaviorScoreUnavailableReason()).isEqualTo(reason);
        assertThat(agent.behaviorSummary().behaviorScoreStatus()).isEqualTo(status);
        assertThat(agent.behaviorSummary().behaviorRiskScore()).isNull();
        assertThat(agent.behaviorSummary().modelVersion()).isNull();
        assertThat(agent.behaviorSummary().featureVersion()).isNull();
        assertThat(audit.lastHoldId).isEqualTo(123);
    }

    private static class HoldStub implements TransferHoldUseCase {
        @Override
        public TransferHoldForFds findForFds(long id) {
            return new TransferHoldForFds(
                    id,
                    7,
                    "flow-123",
                    "HELD",
                    LocalDateTime.of(2026, 11, 9, 14, 0),
                    500000,
                    "0881234567",
                    "A",
                    101L,
                    "088",
                    "ON_APPROVAL",
                    null,
                    null,
                    null,
                    3,
                    1_500_000L,
                    true,
                    true);
        }

        @Override
        public TransferHoldExecutionResult release(long id, long actor) {
            throw new AssertionError();
        }

        @Override
        public void reject(long id, long actor, String reason) {
            throw new AssertionError();
        }

        @Override
        public void cancel(long id, long customer) {
            throw new AssertionError();
        }
    }

    private static class FlowStub implements BehaviorFlowPort {
        @Override
        public Flow find(String id) {
            return new Flow(id, 7, LocalDateTime.of(2026, 11, 9, 13, 58), LocalDateTime.of(2026, 11, 9, 14, 0), 71);
        }

        @Override
        public List<Event> events(String id) {
            return List.of(new Event(1, "STEP_BACK", LocalDateTime.of(2026, 11, 9, 13, 59), "D-01"));
        }

        @Override
        public void create(String id, long customer, LocalDateTime now) {
            throw new AssertionError();
        }

        @Override
        public void lock(String id) {
            throw new AssertionError();
        }

        @Override
        public void append(String id, Event event, LocalDateTime now) {
            throw new AssertionError();
        }

        @Override
        public void finalizeFlow(String id, Integer active, LocalDateTime now) {
            throw new AssertionError();
        }
    }

    private static class ScoreStub implements BehaviorScorePort {
        BehaviorScoreView view;

        @Override
        public BehaviorScoreView find(long id) {
            return view;
        }

        @Override
        public void createIfAbsent(long id, String flowId, LocalDateTime now) {
            throw new AssertionError();
        }

        @Override
        public boolean markStarted(long id, LocalDateTime now) {
            throw new AssertionError();
        }

        @Override
        public boolean available(long id, BehaviorScoreInferencePort.Score score, LocalDateTime now) {
            throw new AssertionError();
        }

        @Override
        public boolean unavailable(long id, String reason, LocalDateTime now) {
            throw new AssertionError();
        }

        @Override
        public List<Long> startedBefore(LocalDateTime cutoff, int limit) {
            throw new AssertionError();
        }
    }

    private static class AuditStub extends AuditLogService {
        long lastHoldId;
        Long lastAdminId;
        String lastIp;

        AuditStub() {
            super(null, new NoopTransactions(), Clock.systemDefaultZone());
        }

        @Override
        public void recordPersonalDataAccess(
                Long adminId,
                PersonalDataAccessEventType type,
                String ip,
                boolean success,
                Map<String, Object> detail,
                AccessReason reason) {
            assertThat(type).isEqualTo(PersonalDataAccessEventType.FDS_HOLD_DETAIL_VIEW);
            assertThat(reason).isEqualTo(AccessReason.OPERATION);
            lastHoldId = ((Number) detail.get("transferHoldId")).longValue();
            lastAdminId = adminId;
            lastIp = ip;
        }
    }

    private static class NoopTransactions implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    }
}
