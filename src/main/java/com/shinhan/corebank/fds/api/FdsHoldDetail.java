package com.shinhan.corebank.fds.api;

import java.util.List;

/** 보류 원본과 저장된 행동 점수를 분리해 보여주는 관리자 상세 응답이다. */
public record FdsHoldDetail(
        long transferHoldId,
        String holdStatus,
        long amount,
        String recipientAccountMasked,
        ServerPolicy serverPolicy,
        Object behaviorSummary,
        String behaviorScoreStatus,
        Double behaviorRiskScore,
        String modelVersion,
        String featureVersion,
        String behaviorScoreUnavailableReason,
        String investigationStatus,
        List<Object> secondaryFindings,
        List<Object> callRecords) {
    public record ServerPolicy(String policyCode) {}
}
