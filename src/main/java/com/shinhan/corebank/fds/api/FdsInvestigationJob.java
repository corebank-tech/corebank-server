package com.shinhan.corebank.fds.api;

import java.time.LocalDateTime;

/** 보류 사건당 하나인 내구성 있는 Agent 조사 작업의 읽기 모델이다. */
public record FdsInvestigationJob(
        long transferHoldId,
        String investigationId,
        String status,
        int attemptCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime leaseExpiresAt,
        String lastFailureReason) {}
