package com.shinhan.corebank.fds.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shinhan.corebank.fds.adapter.in.event.TransferHeldScoreListener;
import com.shinhan.corebank.fds.adapter.in.scheduler.BehaviorScoreRecoveryScheduler;
import com.shinhan.corebank.fds.api.BehaviorScoreView;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Event;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Flow;
import com.shinhan.corebank.fds.application.port.out.BehaviorScoreInferencePort;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import com.shinhan.corebank.transfer.api.TransferHeld;
import com.shinhan.corebank.transfer.api.TransferHoldExecutionResult;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldReleaseMode;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** 추론 실패와 중복 전달에도 점수가 한 번만 확정되는지 확인한다. */
class BehaviorScoreServiceTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T05:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final TransferHeld held = new TransferHeld(
            123,
            7,
            "A",
            TransferHoldReleaseMode.ON_APPROVAL,
            null,
            "3f1c2b7e-8a4d-4f0e-9c1a-2b6d5e7f8a90",
            LocalDateTime.now(clock));
    private final FlowStub flows = new FlowStub();
    private final ScoreStub scores = new ScoreStub();
    private final InferenceStub inference = new InferenceStub();
    private BehaviorScoreService service;
    private long holdCustomerId = 7;
    private String holdFlowId = held.flowId();
    private String holdStatus = "HELD";

    @BeforeEach
    void setup() {
        TransferHoldUseCase holdApi = new TransferHoldUseCase() {
            @Override
            public TransferHoldForFds findForFds(long id) {
                return new TransferHoldForFds(
                        id, holdCustomerId, holdFlowId, holdStatus, held.heldAt(), 500000, "0881234567", "A");
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
        };
        PlatformTransactionManager manager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition def) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {}

            @Override
            public void rollback(TransactionStatus status) {}
        };
        service = new BehaviorScoreService(holdApi, flows, scores, inference, manager, clock);
    }

    @Test
    void storesValidScoreOnlyOnce() {
        completeFlow();
        inference.result = BehaviorScoreInferencePort.Result.available(
                new BehaviorScoreInferencePort.Score(0.81, "xgb-demo-v1", "p6-behavior-v1"));
        service.onHeld(held);
        service.onHeld(held);
        assertThat(inference.calls).isEqualTo(1);
        assertThat(inference.last.features()).isEqualTo(new BehaviorScoreInferencePort.Features(1, 1, 71, 15, 100));
        assertThat(service.findByTransferHoldId(123).status()).isEqualTo("AVAILABLE");
    }

    @Test
    void noFlowSkipsPython() {
        service.onHeld(held);
        assertThat(inference.calls).isZero();
        assertThat(scores.view.unavailableReason()).isEqualTo("FLOW_UNAVAILABLE");
    }

    @Test
    void timeoutDoesNotRunOrRejectTransfer() {
        completeFlow();
        inference.result = BehaviorScoreInferencePort.Result.unavailable(BehaviorScoreInferencePort.Failure.TIMEOUT);
        service.onHeld(held);
        assertThat(scores.view.status()).isEqualTo("UNAVAILABLE");
        assertThat(scores.view.unavailableReason()).isEqualTo("INFERENCE_TIMEOUT");
    }

    @Test
    void mismatchedHoldDoesNotCreateScore() {
        holdCustomerId = 8;
        service.onHeld(held);
        assertThat(scores.view).isNull();
        assertThat(inference.calls).isZero();
    }

    @Test
    void listenerDelegatesHeldEventAndMismatchedFlowIsIgnored() {
        holdFlowId = "different-flow";
        new TransferHeldScoreListener(service).on(held);
        assertThat(scores.view).isNull();
    }

    @Test
    void closedHoldIsUnavailableWithoutPythonCall() {
        holdStatus = "CANCELLED";
        service.onHeld(held);
        assertThat(scores.view.unavailableReason()).isEqualTo("HOLD_CLOSED_BEFORE_SCORING");
        assertThat(inference.calls).isZero();
    }

    @Test
    void incompleteFlowAndInvalidFeaturesSkipPython() {
        completeFlow();
        flows.flow = new Flow(held.flowId(), 7, held.heldAt().minusSeconds(10), null, null);
        service.onHeld(held);
        assertThat(scores.view.unavailableReason()).isEqualTo("FLOW_UNAVAILABLE");

        scores.view = null;
        completeFlow();
        flows.events = List.of(new Event(1, "STEP_BACK", held.heldAt(), "D-01"));
        service.onHeld(held);
        assertThat(scores.view.unavailableReason()).isEqualTo("INVALID_FEATURES");
        assertThat(inference.calls).isZero();
    }

    @ParameterizedTest
    @EnumSource(BehaviorScoreInferencePort.Failure.class)
    void pythonFailuresBecomeUnavailable(BehaviorScoreInferencePort.Failure failure) {
        completeFlow();
        inference.result = BehaviorScoreInferencePort.Result.unavailable(failure);
        service.onHeld(held);
        assertThat(scores.view.status()).isEqualTo("UNAVAILABLE");
        assertThat(inference.calls).isEqualTo(1);
    }

    @Test
    void thrownInferenceIsUnavailableAndNeverRetried() {
        completeFlow();
        inference.throwOnCall = true;
        service.onHeld(held);
        service.onHeld(held);
        assertThat(scores.view.unavailableReason()).isEqualTo("INFERENCE_FAILED");
        assertThat(inference.calls).isEqualTo(1);
    }

    @Test
    void interruptedAttemptIsRecoveredWithoutAnotherInference() {
        scores.createIfAbsent(123, held.flowId(), LocalDateTime.now(clock));
        scores.markStarted(123, LocalDateTime.now(clock).minusMinutes(2));
        new BehaviorScoreRecoveryScheduler(service).recover();
        assertThat(scores.view.unavailableReason()).isEqualTo("SCORING_INTERRUPTED");
        assertThat(inference.calls).isZero();
    }

    @Test
    void saveFailureUsesUnavailableReason() {
        completeFlow();
        inference.result = BehaviorScoreInferencePort.Result.available(
                new BehaviorScoreInferencePort.Score(0.81, "xgb-demo-v1", "p6-behavior-v1"));
        scores.failAvailable = true;
        service.onHeld(held);
        assertThat(scores.view.unavailableReason()).isEqualTo("SCORE_SAVE_FAILED");
    }

    @Test
    void malformedFlowIdDoesNotReachPython() {
        String invalid = "not-a-uuid";
        holdFlowId = invalid;
        TransferHeld badEvent =
                new TransferHeld(123, 7, "A", TransferHoldReleaseMode.ON_APPROVAL, null, invalid, held.heldAt());
        completeFlow();
        flows.flow = new Flow(invalid, 7, flows.flow.firstReceivedAt(), held.heldAt(), 71);
        service.onHeld(badEvent);
        assertThat(scores.view.unavailableReason()).isEqualTo("INVALID_FEATURES");
        assertThat(inference.calls).isZero();
    }

    private void completeFlow() {
        LocalDateTime start = held.heldAt().minusSeconds(100);
        flows.flow = new Flow(held.flowId(), 7, start, held.heldAt(), 71);
        flows.events = List.of(
                new Event(1, "TRANSFER_ENTER", start, "D-01"),
                new Event(2, "RECIPIENT_CONFIRMED", start.plusSeconds(15), "D-01"),
                new Event(3, "STEP_BACK", start.plusSeconds(50), "D-01"),
                new Event(4, "AMOUNT_EDITED", start.plusSeconds(60), "D-01"),
                new Event(5, "SUBMIT_REQUESTED", start.plusSeconds(100), "D-02"));
    }

    private static class InferenceStub implements BehaviorScoreInferencePort {
        int calls;
        Request last;
        Result result;
        boolean throwOnCall;

        @Override
        public Result infer(Request request) {
            calls++;
            last = request;
            if (throwOnCall) throw new IllegalStateException("test inference failure");
            return result;
        }
    }

    private static class FlowStub implements BehaviorFlowPort {
        Flow flow;
        List<Event> events = List.of();

        @Override
        public Flow find(String id) {
            return flow;
        }

        @Override
        public List<Event> events(String id) {
            return events;
        }

        @Override
        public void create(String id, long customer, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void lock(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void append(String id, Event event, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void finalizeFlow(String id, Integer active, LocalDateTime now) {
            throw new UnsupportedOperationException();
        }
    }

    private static class ScoreStub implements BehaviorScorePort {
        BehaviorScoreView view;
        boolean started;
        boolean failAvailable;

        @Override
        public void createIfAbsent(long id, String flowId, LocalDateTime now) {
            if (view == null) view = new BehaviorScoreView(id, "PENDING", null, null, null, null);
        }

        @Override
        public boolean markStarted(long id, LocalDateTime now) {
            if (started || !"PENDING".equals(view.status())) return false;
            started = true;
            return true;
        }

        @Override
        public boolean available(long id, BehaviorScoreInferencePort.Score score, LocalDateTime now) {
            if (failAvailable) throw new IllegalStateException("test storage failure");
            if (!"PENDING".equals(view.status())) return false;
            view = new BehaviorScoreView(
                    id, "AVAILABLE", score.value(), score.modelVersion(), score.featureVersion(), null);
            return true;
        }

        @Override
        public boolean unavailable(long id, String reason, LocalDateTime now) {
            if (!"PENDING".equals(view.status())) return false;
            view = new BehaviorScoreView(id, "UNAVAILABLE", null, null, null, reason);
            return true;
        }

        @Override
        public BehaviorScoreView find(long id) {
            return view;
        }

        @Override
        public List<Long> startedBefore(LocalDateTime cutoff, int limit) {
            return started && "PENDING".equals(view.status()) ? List.of(view.transferHoldId()) : List.of();
        }
    }
}
