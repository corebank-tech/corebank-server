package com.shinhan.corebank.fds.application.service;

import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.audit.PersonalDataAccessEventType;
import com.shinhan.corebank.fds.api.FdsHoldListPage;
import com.shinhan.corebank.fds.application.port.out.BehaviorScorePort;
import com.shinhan.corebank.fds.application.port.out.FdsAdminReadPort;
import com.shinhan.corebank.fds.application.port.out.FdsInvestigationJobPort;
import com.shinhan.corebank.transfer.api.TransferHoldForFds;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** P6 조사 사건을 기준으로 P4 공개 원본을 결합해 관리자 목록을 만든다. */
@Service
@RequiredArgsConstructor
public class FdsHoldListService {
    private final FdsAdminReadPort reads;
    private final TransferHoldUseCase holds;
    private final FdsInvestigationJobPort jobs;
    private final BehaviorScorePort scores;
    private final FdsRiskAccountService risks;
    private final AuditLogService audit;

    @Transactional(readOnly = true)
    public FdsHoldListPage find(
            int page,
            int size,
            String holdStatus,
            String policyCode,
            String riskStatus,
            String investigationStatus,
            long adminId,
            String requestIp,
            AccessReason reason) {
        List<FdsHoldListPage.FdsHoldListItem> filtered = reads.findHoldIds().stream()
                .map(this::item)
                .filter(item -> matches(holdStatus, item.holdStatus()))
                .filter(item -> matches(policyCode, item.policyCode()))
                .filter(item -> matches(riskStatus, item.riskAccountStatus()))
                .filter(item -> matches(investigationStatus, item.investigationStatus()))
                .toList();
        int from = Math.min(page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        audit.recordPersonalDataAccess(
                adminId,
                PersonalDataAccessEventType.FDS_HOLD_LIST_VIEW,
                requestIp,
                true,
                Map.of("page", page, "size", size),
                reason);
        return new FdsHoldListPage(
                filtered.subList(from, to),
                page,
                size,
                filtered.size(),
                filtered.isEmpty() ? 0 : (filtered.size() + size - 1) / size);
    }

    private FdsHoldListPage.FdsHoldListItem item(long holdId) {
        TransferHoldForFds hold = holds.findForFds(holdId);
        var job = jobs.findByHoldId(holdId);
        var score = scores.find(holdId);
        var risk = risks.check(hold.recipientBankCode(), hold.depositAccountNumber());
        var calls = reads.findCallRecords(holdId);
        var action = reads.findLatestAction(holdId);
        String path = path(hold.status(), risk.status(), action == null ? null : action.actionStatus());
        return new FdsHoldListPage.FdsHoldListItem(
                holdId,
                hold.heldAt(),
                hold.amount(),
                hold.recipientBankCode(),
                mask(hold.depositAccountNumber()),
                hold.policyCode(),
                hold.status(),
                job == null ? null : job.status(),
                score == null ? "PENDING" : score.status(),
                risk.status(),
                risk.riskAccountId(),
                risk.datasetVersion(),
                calls.isEmpty() ? null : calls.getFirst().callResult(),
                action == null ? null : action.actionStatus(),
                path);
    }

    private static boolean matches(String expected, String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    private static String path(String holdStatus, String riskStatus, String actionStatus) {
        if (actionStatus != null && ("PENDING".equals(actionStatus) || "CONFLICT".equals(actionStatus))) {
            return "RECOVERY_REQUIRED";
        }
        if (!"HELD".equals(holdStatus)) return "COMPLETED";
        if ("MATCH".equals(riskStatus)) return "MATCH_REJECT_ONLY";
        return "CALL_THEN_DECIDE";
    }

    private static String mask(String value) {
        if (value == null || value.length() < 4) return "****";
        return "***-**-" + value.substring(value.length() - 4);
    }
}
