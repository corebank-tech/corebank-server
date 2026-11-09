package com.shinhan.corebank.fds.api;

/** Agent 작업에 건네는 보류 원본과 저장 완료된 행동 점수의 읽기 전용 입력이다. */
public record FdsInvestigationInput(
        String investigationId,
        long transferHoldId,
        String policyCode,
        BehaviorSummary behaviorSummary,
        TransferSummary transferSummary) {
    public record BehaviorSummary(
            Integer backCount,
            Integer amountRevisionCount,
            Integer confirmActiveSeconds,
            String behaviorScoreStatus,
            Double behaviorRiskScore,
            String modelVersion,
            String featureVersion) {}

    public record TransferSummary(long amount, String recipientAccountMasked) {}
}
