package com.shinhan.corebank.paymentstop.domain.agent;

import java.time.LocalDateTime;

/** Agent 실행 한 번. 대기열의 행이기도 하다(QUEUED). */
public record AgentRun(
        long runId,
        long objectionId,
        AgentRunStatus status,
        int attempt,
        LocalDateTime leaseUntil,
        String model,
        String promptVersion,
        Long inputTokens,
        Long outputTokens,
        Long costMicroUsd,
        long requestedBy,
        LocalDateTime queuedAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        String failureReason) {

    public boolean isActive() {
        return status == AgentRunStatus.QUEUED || status == AgentRunStatus.RUNNING;
    }
}
