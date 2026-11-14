package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.fds.api.BehaviorScoreQuery;
import com.shinhan.corebank.fds.api.BehaviorScoreView;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort;
import com.shinhan.corebank.fds.application.port.out.BehaviorFlowPort.Flow;
import com.shinhan.corebank.fds.application.port.out.BehaviorScoreInferencePort;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import com.shinhan.corebank.transfer.api.TransferHeld;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 보류 확정 뒤 행동 피처를 추론하고 점수 상태를 한 번만 확정한다. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.fds.score", name = "enabled", havingValue = "true")
public class BehaviorScoreService implements BehaviorScoreQuery {
    private static final String FEATURE_VERSION = "p6-behavior-v1";

    private final TransferHoldUseCase holdUseCase;
    private final BehaviorFlowPort flows;
    private final BehaviorScorePort scores;
    private final BehaviorScoreInferencePort inference;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public void onHeld(TransferHeld event) {
        TransferHoldForFds hold = holdUseCase.findForFds(event.transferHoldId());
        if (hold.customerId() != event.customerId() || !java.util.Objects.equals(hold.flowId(), event.flowId())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        newTransaction()
                .executeWithoutResult(ignored -> scores.createIfAbsent(event.transferHoldId(), event.flowId(), now));
        if (!"HELD".equals(hold.status())) {
            finishUnavailable(event.transferHoldId(), "HOLD_CLOSED_BEFORE_SCORING");
            return;
        }
        Flow flow;
        try {
            flow = event.flowId() == null ? null : flows.find(event.flowId());
        } catch (RuntimeException e) {
            finishUnavailable(event.transferHoldId(), "FLOW_UNAVAILABLE");
            return;
        }
        if (flow == null || flow.customerId() != event.customerId() || flow.finalizedAt() == null) {
            finishUnavailable(event.transferHoldId(), "FLOW_UNAVAILABLE");
            return;
        }
        BehaviorScoreInferencePort.Features features;
        try {
            BehaviorFeatureExtractor.Features extracted =
                    BehaviorFeatureExtractor.extract(flows.events(event.flowId()), flow);
            features = extracted == null
                    ? null
                    : new BehaviorScoreInferencePort.Features(
                            extracted.backCount(),
                            extracted.amountRevisionCount(),
                            extracted.confirmActiveSeconds(),
                            extracted.recipientConfirmSeconds(),
                            extracted.totalFlowSeconds());
        } catch (RuntimeException e) {
            finishUnavailable(event.transferHoldId(), "INVALID_FEATURES");
            return;
        }
        if (features == null) {
            finishUnavailable(event.transferHoldId(), "INVALID_FEATURES");
            return;
        }
        // 시작 표식을 먼저 커밋해 응답 유실이나 조사 재시도에도 Python을 다시 호출하지 않는다.
        UUID flowId;
        try {
            flowId = UUID.fromString(event.flowId());
        } catch (IllegalArgumentException e) {
            finishUnavailable(event.transferHoldId(), "INVALID_FEATURES");
            return;
        }
        Boolean claimed = newTransaction()
                .execute(ignored -> scores.markStarted(event.transferHoldId(), LocalDateTime.now(clock)));
        if (!Boolean.TRUE.equals(claimed)) {
            return;
        }
        BehaviorScoreInferencePort.Result result;
        try {
            result = inference.infer(
                    new BehaviorScoreInferencePort.Request(event.transferHoldId(), flowId, FEATURE_VERSION, features));
        } catch (RuntimeException e) {
            finishUnavailable(event.transferHoldId(), "INFERENCE_FAILED");
            return;
        }
        if (result.score() != null) {
            try {
                newTransaction()
                        .executeWithoutResult(ignored ->
                                scores.available(event.transferHoldId(), result.score(), LocalDateTime.now(clock)));
            } catch (RuntimeException e) {
                finishUnavailable(event.transferHoldId(), "SCORE_SAVE_FAILED");
            }
        } else {
            finishUnavailable(
                    event.transferHoldId(),
                    switch (result.failure()) {
                        case INPUT_INVALID -> "INFERENCE_INPUT_INVALID";
                        case SERVICE_UNAVAILABLE -> "INFERENCE_SERVICE_UNAVAILABLE";
                        case MODEL_UNAVAILABLE -> "MODEL_UNAVAILABLE";
                        case TIMEOUT -> "INFERENCE_TIMEOUT";
                        case INFERENCE_FAILED -> "INFERENCE_FAILED";
                    });
        }
    }

    private void finishUnavailable(long holdId, String reason) {
        newTransaction().executeWithoutResult(ignored -> scores.unavailable(holdId, reason, LocalDateTime.now(clock)));
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    public void recoverInterrupted() {
        for (Long holdId : scores.startedBefore(LocalDateTime.now(clock).minusMinutes(1), 100)) {
            finishUnavailable(holdId, "SCORING_INTERRUPTED");
        }
    }

    @Override
    public BehaviorScoreView findByTransferHoldId(long transferHoldId) {
        return scores.find(transferHoldId);
    }
}
