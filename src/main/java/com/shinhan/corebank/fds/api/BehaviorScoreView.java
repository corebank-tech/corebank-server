package com.shinhan.corebank.fds.api;

/** 관리자 상세와 Agent 입력은 이 저장 결과만 사용한다. */
public record BehaviorScoreView(
        long transferHoldId,
        String status,
        Double behaviorRiskScore,
        String modelVersion,
        String featureVersion,
        String unavailableReason) {}
