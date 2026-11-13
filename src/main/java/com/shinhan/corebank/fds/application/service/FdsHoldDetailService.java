package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.audit.PersonalDataAccessEventType;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.util.MaskingUtil;
import com.shinhan.corebank.fds.api.BehaviorScoreView;
import com.shinhan.corebank.fds.api.FdsHoldDetail;
import com.shinhan.corebank.fds.api.FdsInvestigationInput;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import com.shinhan.corebank.fds.application.port.out.FdsAdminReadPort;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.fds.domain.exception.FdsErrorCode;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P4 원본과 조사 당시 P6 스냅샷을 결합하되 외부 Tool은 다시 호출하지 않는다. */
@Service
public class FdsHoldDetailService {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final TransferHoldUseCase holds;
    private final BehaviorScorePort scores;
    private final AuditLogService audit;
    private final FdsRiskAccountService risks;
    private final FdsInvestigationJobPort jobs;
    private final FdsAdminReadPort reads;
    private final FdsInvestigationInputService inputs;

    @Autowired
    public FdsHoldDetailService(
            TransferHoldUseCase holds,
            BehaviorScorePort scores,
            AuditLogService audit,
            FdsRiskAccountService risks,
            FdsInvestigationJobPort jobs,
            FdsAdminReadPort reads,
            FdsInvestigationInputService inputs) {
        this.holds = holds;
        this.scores = scores;
        this.audit = audit;
        this.risks = risks;
        this.jobs = jobs;
        this.reads = reads;
        this.inputs = inputs;
    }

    FdsHoldDetailService(TransferHoldUseCase holds, BehaviorScorePort scores, AuditLogService audit) {
        this(holds, scores, audit, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public FdsHoldDetail get(long holdId, long adminId, String requestIp, AccessReason reason) {
        TransferHoldForFds hold = findHold(holdId);
        BehaviorScoreView score = scores.find(holdId);
        audit.recordPersonalDataAccess(
                adminId,
                PersonalDataAccessEventType.FDS_HOLD_DETAIL_VIEW,
                requestIp,
                true,
                Map.of("transferHoldId", holdId),
                reason);
        var risk = risks == null
                ? FdsRiskAccountService.Check.unavailable(null, "ACTIVE_DATASET_MISSING")
                : risks.check(hold.recipientBankCode(), hold.depositAccountNumber());
        var job = jobs == null ? null : jobs.findByHoldId(holdId);
        String investigationId = job == null ? null : job.investigationId();
        FdsAdminReadPort.Report storedReport =
                reads == null || investigationId == null ? null : reads.findReport(investigationId);
        FdsAdminReadPort.ToolResults storedTools =
                reads == null || investigationId == null ? null : reads.findToolResults(investigationId);
        boolean available = score != null && "AVAILABLE".equals(score.status());
        FdsHoldDetail.BehaviorScore behaviorScore = new FdsHoldDetail.BehaviorScore(
                score == null ? "PENDING" : score.status(),
                available ? score.behaviorRiskScore() : null,
                available ? score.modelVersion() : null,
                available ? score.featureVersion() : null,
                score != null && "UNAVAILABLE".equals(score.status()) ? score.unavailableReason() : null);
        FdsHoldDetail.OriginalTransfer originalTransfer = original(hold);
        FdsHoldDetail.AgentReport agentReport = report(investigationId, storedReport);
        Set<Long> citedEvidence = citedEvidence(agentReport);
        return new FdsHoldDetail(
                hold.transferHoldId(),
                hold.amount(),
                originalTransfer.holdStatus(),
                originalTransfer.recipientAccountMasked(),
                job == null ? null : job.status(),
                behaviorScore.status(),
                behaviorScore.score(),
                behaviorScore.modelVersion(),
                behaviorScore.featureVersion(),
                behaviorScore.unavailableReason(),
                agentReport == null ? List.of() : agentReport.secondaryFindings(),
                originalTransfer,
                policy(hold),
                behavior(hold),
                behaviorScore,
                new FdsHoldDetail.RiskAccountCheck(
                        risk.status(), risk.riskAccountId(), risk.datasetVersion(), risk.unavailableReason(), true),
                job == null
                        ? null
                        : new FdsHoldDetail.Investigation(
                                job.investigationId(),
                                job.status(),
                                job.attemptCount(),
                                job.nextAttemptAt(),
                                job.leaseExpiresAt(),
                                job.lastFailureReason()),
                tools(storedTools, citedEvidence),
                agentReport,
                reads == null
                        ? List.of()
                        : reads.findCallRecords(holdId).stream()
                                .map(FdsHoldDetailService::call)
                                .toList(),
                reads == null ? null : action(reads.findLatestAction(holdId)));
    }

    private TransferHoldForFds findHold(long holdId) {
        try {
            return holds.findForFds(holdId);
        } catch (BusinessException exception) {
            throw new BusinessException(FdsErrorCode.NOT_FOUND, exception);
        }
    }

    private FdsInvestigationInput.BehaviorSummary behavior(TransferHoldForFds hold) {
        return inputs == null
                ? new FdsInvestigationInput.BehaviorSummary(null, null, null, "PENDING", null, null, null)
                : inputs.behaviorSummary(hold);
    }

    private static FdsHoldDetail.OriginalTransfer original(TransferHoldForFds hold) {
        Long actor = "CANCELLED".equals(hold.status()) ? null : hold.actorId();
        return new FdsHoldDetail.OriginalTransfer(
                hold.transferHoldId(),
                hold.status(),
                hold.heldAt(),
                hold.amount(),
                hold.recipientBankCode(),
                mask(hold.depositAccountNumber()),
                hold.releaseMode(),
                actor,
                hold.reasonCode(),
                hold.completedAt());
    }

    private static FdsHoldDetail.ServerPolicy policy(TransferHoldForFds hold) {
        return new FdsHoldDetail.ServerPolicy(
                hold.policyCode(),
                hold.policyCount10m(),
                hold.policyAmount10m(),
                hold.policyHesitationInWindow(),
                hold.policyNewRecipientAtWindowStart());
    }

    private static FdsHoldDetail.ToolResults tools(FdsAdminReadPort.ToolResults stored, Set<Long> evidence) {
        if (stored == null) return new FdsHoldDetail.ToolResults(emptyHistory(), emptyRecipient());
        FdsAdminReadPort.History history = stored.history();
        FdsAdminReadPort.Recipient recipient = stored.recipient();
        return new FdsHoldDetail.ToolResults(
                history == null
                        ? emptyHistory()
                        : new FdsHoldDetail.HistoryTool(
                                true,
                                history.rangeFrom(),
                                history.rangeTo(),
                                history.hasMore(),
                                history.providedAt(),
                                history.items().stream()
                                        .map(item -> new FdsHoldDetail.HistoryItem(
                                                item.refId(),
                                                item.amount(),
                                                item.transferredAt(),
                                                item.recipientAccountMasked(),
                                                evidence.contains(item.refId())))
                                        .toList()),
                recipient == null
                        ? emptyRecipient()
                        : new FdsHoldDetail.RecipientContextTool(
                                true,
                                recipient.firstSucceededAt(),
                                recipient.hasSucceededBeforeWindow(),
                                recipient.otherWithdrawalAccountCount(),
                                recipient.providedAt()));
    }

    private static Set<Long> citedEvidence(FdsHoldDetail.AgentReport report) {
        if (report == null) return Set.of();
        return report.secondaryFindings().stream()
                .flatMap(finding -> finding.evidenceTransferIds().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static FdsHoldDetail.AgentReport report(String investigationId, FdsAdminReadPort.Report stored) {
        if (stored == null) return null;
        try {
            JsonNode root = JSON.readTree(stored.reportJson());
            List<FdsHoldDetail.Finding> findings = new java.util.ArrayList<>();
            for (JsonNode finding : root.path("secondaryFindings")) {
                findings.add(new FdsHoldDetail.Finding(
                        finding.path("code").asText(),
                        finding.path("description").asText(),
                        finding.path("confidence").asText(),
                        longs(finding.path("evidenceTransferIds"))));
            }
            return new FdsHoldDetail.AgentReport(
                    stored.schemaVersion(),
                    investigationId,
                    root.path("status").asText(),
                    List.copyOf(findings),
                    strings(root.path("missingInformation")),
                    strings(root.path("suggestedQuestions")),
                    root.path("summary").asText(),
                    stored.completedAt());
        } catch (RuntimeException exception) {
            throw new BusinessException(FdsErrorCode.INVALID_INVESTIGATION_REPORT, exception);
        }
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode value : node) values.add(value.asText());
        return List.copyOf(values);
    }

    private static List<Long> longs(JsonNode node) {
        List<Long> values = new java.util.ArrayList<>();
        for (JsonNode value : node) values.add(value.asLong());
        return List.copyOf(values);
    }

    private static FdsHoldDetail.CallRecord call(FdsAdminReadPort.CallRecord call) {
        return new FdsHoldDetail.CallRecord(
                call.callRecordId(),
                call.callResult(),
                call.adminId(),
                call.investigationReason(),
                call.recordedAt(),
                call.selectedByAction());
    }

    private static FdsHoldDetail.AdminAction action(FdsAdminReadPort.AdminAction action) {
        if (action == null) return null;
        return new FdsHoldDetail.AdminAction(
                action.actionId(),
                action.actionType(),
                action.actionStatus(),
                action.actorId(),
                action.reasonCode(),
                action.callRecordId(),
                action.riskStatus(),
                action.riskAccountId(),
                action.datasetVersion(),
                action.p4Status(),
                action.p4ActorId(),
                action.p4ReasonCode(),
                action.failureReason(),
                action.createdAt(),
                action.updatedAt(),
                "PENDING".equals(action.actionStatus()) || "CONFLICT".equals(action.actionStatus()));
    }

    private static FdsHoldDetail.HistoryTool emptyHistory() {
        return new FdsHoldDetail.HistoryTool(false, null, null, false, null, List.of());
    }

    private static FdsHoldDetail.RecipientContextTool emptyRecipient() {
        return new FdsHoldDetail.RecipientContextTool(false, null, null, null, null);
    }

    private static String mask(String accountNumber) {
        try {
            return MaskingUtil.maskAccountNumber(accountNumber);
        } catch (IllegalArgumentException exception) {
            return "확인 불가";
        }
    }
}
