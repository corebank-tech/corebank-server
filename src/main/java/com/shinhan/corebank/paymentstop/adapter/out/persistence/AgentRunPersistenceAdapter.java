package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.application.port.out.AgentRunPort;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRun;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStatus;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStep;
import com.shinhan.corebank.paymentstop.domain.agent.ReviewDraft;
import com.shinhan.corebank.paymentstop.domain.gate.GateBranch;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentRunPersistenceAdapter implements AgentRunPort {

    private final AgentRunJpaRepository runRepository;
    private final AgentRunStepJpaRepository stepRepository;
    private final ReviewDraftJpaRepository draftRepository;

    @Override
    public long enqueue(long objectionId, long requestedBy, LocalDateTime queuedAt) {
        return runRepository
                .save(AgentRunJpaEntity.queued(objectionId, requestedBy, queuedAt))
                .getRunId();
    }

    @Override
    public Optional<AgentRun> findRun(long runId) {
        return runRepository.findById(runId).map(AgentRunPersistenceAdapter::toRun);
    }

    @Override
    public Optional<AgentRun> findRunForUpdate(long runId) {
        return runRepository.findByIdForUpdate(runId).map(AgentRunPersistenceAdapter::toRun);
    }

    @Override
    public List<AgentRun> findRunsByObjection(long objectionId) {
        return runRepository.findByObjectionIdOrderByRunIdDesc(objectionId).stream()
                .map(AgentRunPersistenceAdapter::toRun)
                .toList();
    }

    @Override
    public Optional<AgentRun> findLatestRun(long objectionId) {
        return runRepository.findFirstByObjectionIdOrderByRunIdDesc(objectionId).map(AgentRunPersistenceAdapter::toRun);
    }

    @Override
    public List<Long> failExhausted(LocalDateTime now, int maxAttempts, String reason) {
        List<Long> closed = new ArrayList<>();
        for (Long runId : runRepository.findExhaustedIds(now, maxAttempts, AgentRunStatus.RUNNING)) {
            if (runRepository.failExhaustedById(
                            runId, now, maxAttempts, reason, AgentRunStatus.RUNNING, AgentRunStatus.FAILED)
                    == 1) {
                closed.add(runId);
            }
        }
        return closed;
    }

    @Override
    public List<Long> findClaimCandidates(LocalDateTime now, int maxAttempts, int limit) {
        return runRepository.findClaimCandidates(
                now, maxAttempts, AgentRunStatus.QUEUED, AgentRunStatus.RUNNING, PageRequest.of(0, limit));
    }

    @Override
    public boolean tryClaim(long runId, LocalDateTime now, LocalDateTime leaseUntil, int maxAttempts) {
        return runRepository.tryClaim(
                        runId, now, leaseUntil, maxAttempts, AgentRunStatus.QUEUED, AgentRunStatus.RUNNING)
                == 1;
    }

    @Override
    public void extendLease(long runId, LocalDateTime leaseUntil) {
        entity(runId).extendLease(leaseUntil);
    }

    @Override
    public void requeue(long runId, String reason) {
        entity(runId).requeue(reason);
    }

    @Override
    public void fail(long runId, LocalDateTime endedAt, String reason) {
        entity(runId).fail(endedAt, reason);
    }

    @Override
    public void complete(
            long runId,
            LocalDateTime endedAt,
            String model,
            String promptVersion,
            Long inputTokens,
            Long outputTokens,
            Long costMicroUsd) {
        entity(runId).complete(endedAt, model, promptVersion, inputTokens, outputTokens, costMicroUsd);
    }

    @Override
    public int maxSeq(long runId) {
        return stepRepository.maxSeq(runId);
    }

    @Override
    public Optional<AgentRunStep> findStep(long runId, int seq) {
        return stepRepository.findByRunIdAndSeq(runId, seq).map(AgentRunPersistenceAdapter::toStep);
    }

    @Override
    public void insertStep(AgentRunStep step) {
        // 같은 실행의 다음 단계는 실행 행 락 안에서만 넣는다. uk_agent_run_step_seq 는 그 약속이 깨졌을 때 터지는 마지막 방어선이다
        stepRepository.saveAndFlush(AgentRunStepJpaEntity.of(
                step.runId(),
                step.seq(),
                step.type(),
                step.title(),
                step.summary(),
                step.detail(),
                step.status(),
                step.startedAt(),
                step.endedAt()));
    }

    @Override
    public List<AgentRunStep> findStepsAfter(long runId, int afterSeq) {
        return stepRepository.findByRunIdAndSeqGreaterThanOrderBySeqAsc(runId, afterSeq).stream()
                .map(AgentRunPersistenceAdapter::toStep)
                .toList();
    }

    @Override
    public Optional<ReviewDraft> findDraftByRun(long runId) {
        return draftRepository.findByRunId(runId).map(AgentRunPersistenceAdapter::toDraft);
    }

    @Override
    public Optional<ReviewDraft> findLatestDraft(long objectionId) {
        return draftRepository
                .findFirstByObjectionIdOrderByReviewDraftIdDesc(objectionId)
                .map(AgentRunPersistenceAdapter::toDraft);
    }

    @Override
    public void saveDraft(ReviewDraft draft) {
        draftRepository.save(ReviewDraftJpaEntity.of(
                draft.runId(), draft.objectionId(), draft.branch().name(), draft.content(), draft.submittedAt()));
    }

    private AgentRunJpaEntity entity(long runId) {
        return runRepository
                .findById(runId)
                .orElseThrow(() -> new IllegalStateException("분석 실행이 없습니다. runId=" + runId));
    }

    private static AgentRun toRun(AgentRunJpaEntity entity) {
        return new AgentRun(
                entity.getRunId(),
                entity.getObjectionId(),
                entity.getStatus(),
                entity.getAttempt(),
                entity.getLeaseUntil(),
                entity.getModel(),
                entity.getPromptVersion(),
                entity.getInputTokens(),
                entity.getOutputTokens(),
                entity.getCostMicroUsd(),
                entity.getRequestedBy(),
                entity.getQueuedAt(),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getFailureReason());
    }

    private static AgentRunStep toStep(AgentRunStepJpaEntity entity) {
        return new AgentRunStep(
                entity.getRunId(),
                entity.getSeq(),
                entity.getType(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getDetail(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getEndedAt());
    }

    private static ReviewDraft toDraft(ReviewDraftJpaEntity entity) {
        return new ReviewDraft(
                entity.getRunId(),
                entity.getObjectionId(),
                GateBranch.valueOf(entity.getBranch()),
                entity.getContent(),
                entity.getSubmittedAt());
    }
}
