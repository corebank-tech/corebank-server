package com.shinhan.corebank.fds.api;

/** Agent 작업에 건네는 보류 원본과 저장 완료된 행동 점수의 읽기 전용 입력이다. */
public record FdsInvestigationInput(
        String investigationId,
        long transferHoldId,
        Integer attemptNo,
        String policyCode,
        PolicyEvidence policyEvidence,
        BehaviorSummary behaviorSummary,
        TransferSummary transferSummary) {
    public FdsInvestigationInput(
            String investigationId,
            long transferHoldId,
            String policyCode,
            PolicyEvidence policyEvidence,
            BehaviorSummary behaviorSummary,
            TransferSummary transferSummary) {
        this(investigationId, transferHoldId, null, policyCode, policyEvidence, behaviorSummary, transferSummary);
    }

    public FdsInvestigationInput withAttemptNo(int attemptNo) {
        return new FdsInvestigationInput(
                investigationId,
                transferHoldId,
                attemptNo,
                policyCode,
                policyEvidence,
                behaviorSummary,
                transferSummary);
    }

    public record PolicyEvidence(int count10m, long amount10m) {}

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
