package com.shinhan.corebank.paymentstop.application.port.out;

import com.shinhan.corebank.paymentstop.domain.agent.AgentRun;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStep;
import com.shinhan.corebank.paymentstop.domain.agent.ReviewDraft;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Agent 실행, 단계, 판정안 저장소. 대기열은 QUEUED 행이다.
public interface AgentRunPort {

    long enqueue(long objectionId, long requestedBy, LocalDateTime queuedAt);

    Optional<AgentRun> findRun(long runId);

    // 단계 순번과 상태 전이를 줄 세우려고 실행 행을 잠근다. 트랜잭션 안에서만 부른다
    Optional<AgentRun> findRunForUpdate(long runId);

    List<AgentRun> findRunsByObjection(long objectionId);

    Optional<AgentRun> findLatestRun(long objectionId);

    // 목록용. 이의제기마다 가장 최근 실행. 이의제기 수와 무관하게 한 번의 조회다
    Map<Long, AgentRun> findLatestRuns(Collection<Long> objectionIds);

    // 목록용. 실행마다 마지막 단계의 제목
    Map<Long, String> findLastStepTitles(Collection<Long> runIds);

    /** 점유가 끝났는데 더 가져갈 수 없는 실행을 FAILED 로 닫는다. 닫은 실행 ID 를 돌려준다. */
    List<Long> failExhausted(LocalDateTime now, int maxAttempts, String reason);

    List<Long> findClaimCandidates(LocalDateTime now, int maxAttempts, int limit);

    /** 조건부 UPDATE 로 가져간다. 다른 워커가 먼저 가져갔으면 false. */
    boolean tryClaim(long runId, LocalDateTime now, LocalDateTime leaseUntil, int maxAttempts);

    void extendLease(long runId, LocalDateTime leaseUntil);

    void requeue(long runId, String reason);

    void fail(long runId, LocalDateTime endedAt, String reason);

    void complete(
            long runId,
            LocalDateTime endedAt,
            String model,
            String promptVersion,
            Long inputTokens,
            Long outputTokens,
            Long costMicroUsd);

    int maxSeq(long runId);

    Optional<AgentRunStep> findStep(long runId, int seq);

    void insertStep(AgentRunStep step);

    List<AgentRunStep> findStepsAfter(long runId, int afterSeq);

    Optional<ReviewDraft> findDraftByRun(long runId);

    Optional<ReviewDraft> findLatestDraft(long objectionId);

    void saveDraft(ReviewDraft draft);
}
