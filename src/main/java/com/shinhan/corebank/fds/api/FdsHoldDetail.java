package com.shinhan.corebank.fds.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 관리자 화면이 서버 근거·Agent 보조 발견·최종 조치를 구분해 표시하는 상세 응답이다. */
public record FdsHoldDetail(
        OriginalTransfer originalTransfer,
        ServerPolicy serverPolicy,
        FdsInvestigationInput.BehaviorSummary behaviorSummary,
        BehaviorScore behaviorScore,
        RiskAccountCheck riskAccountCheck,
        Investigation investigation,
        ToolResults toolResults,
        AgentReport agentReport,
        List<CallRecord> callRecords,
        AdminAction adminAction) {
    /** 기존 FE 계약을 상세 영역 전환 기간에도 유지한다. */
    public String holdStatus() {
        return originalTransfer.holdStatus();
    }

    public String recipientAccountMasked() {
        return originalTransfer.recipientAccountMasked();
    }

    public String behaviorScoreStatus() {
        return behaviorScore.status();
    }

    public Double behaviorRiskScore() {
        return behaviorScore.score();
    }

    public String modelVersion() {
        return behaviorScore.modelVersion();
    }

    public String featureVersion() {
        return behaviorScore.featureVersion();
    }

    public String behaviorScoreUnavailableReason() {
        return behaviorScore.unavailableReason();
    }

    public record OriginalTransfer(
            long transferHoldId,
            String holdStatus,
            LocalDateTime heldAt,
            long amount,
            String recipientBankCode,
            String recipientAccountMasked,
            String releaseMode,
            Long processedActorId,
            String reasonCode,
            LocalDateTime completedAt) {}

    public record ServerPolicy(
            @Schema(allowableValues = {"A", "B"}) String policyCode,
            Integer count10m,
            Long amount10m,
            Boolean hesitationInWindow,
            Boolean newRecipientAtWindowStart) {}

    public record BehaviorScore(
            @Schema(allowableValues = {"PENDING", "AVAILABLE", "UNAVAILABLE"}) String status,
            Double score,
            String modelVersion,
            String featureVersion,
            String unavailableReason) {}

    public record RiskAccountCheck(
            @Schema(allowableValues = {"MATCH", "NO_MATCH", "UNAVAILABLE"}) String status,
            Long riskAccountId,
            String datasetVersion,
            String unavailableReason,
            @Schema(description = "관리자 조치 직전에 활성 데이터셋으로 다시 판정함") boolean revalidateBeforeAction) {}

    public record Investigation(
            String investigationId,
            @Schema(allowableValues = {"PENDING", "RUNNING", "COMPLETED", "FAILED"}) String status,
            Integer attemptCount,
            LocalDateTime nextAttemptAt,
            LocalDateTime leaseExpiresAt,
            String failureReason) {}

    public record ToolResults(HistoryTool history, RecipientContextTool recipientContext) {}

    public record HistoryTool(
            boolean executed,
            LocalDateTime rangeFrom,
            LocalDateTime rangeTo,
            boolean hasMore,
            LocalDateTime providedAt,
            List<HistoryItem> items) {}

    public record HistoryItem(
            long refId,
            long amount,
            LocalDateTime transferredAt,
            String recipientAccountMasked,
            boolean usedAsEvidence) {}

    public record RecipientContextTool(
            boolean executed,
            LocalDateTime firstSucceededAt,
            Boolean hasSucceededBeforeWindow,
            Integer otherWithdrawalAccountCount,
            LocalDateTime providedAt) {}

    public record AgentReport(
            String schemaVersion,
            String investigationId,
            String status,
            List<Finding> secondaryFindings,
            List<String> missingInformation,
            List<String> suggestedQuestions,
            String summary,
            LocalDateTime completedAt) {}

    public record Finding(String code, String description, String confidence, List<Long> evidenceTransferIds) {}

    public record CallRecord(
            long callRecordId,
            String callResult,
            long adminId,
            String investigationReason,
            LocalDateTime recordedAt,
            boolean selectedByAction) {}

    public record AdminAction(
            String actionId,
            String actionType,
            @Schema(allowableValues = {"PENDING", "COMPLETED", "FAILED", "CONFLICT"}) String status,
            Long actorId,
            String reasonCode,
            Long callRecordId,
            String riskStatus,
            Long riskAccountId,
            String datasetVersion,
            String p4Status,
            Long p4ActorId,
            String p4ReasonCode,
            String failureReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean recoveryRequired) {}
}
