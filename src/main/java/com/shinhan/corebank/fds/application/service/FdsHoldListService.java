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
import java.util.Comparator;
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

    @Transactional
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
        List<Candidate> filtered = reads.findHoldIds().stream()
                .map(this::candidate)
                .filter(item -> matches(holdStatus, item.hold().status()))
                .filter(item -> matches(policyCode, item.hold().policyCode()))
                .filter(item -> matches(riskStatus, item.risk().status()))
                .filter(item -> matches(investigationStatus, item.investigationStatus()))
                .sorted(Comparator.comparing((Candidate item) -> item.hold().heldAt())
                        .reversed()
                        .thenComparing(item -> item.hold().transferHoldId(), Comparator.reverseOrder()))
                .toList();
        // 최대 int 페이지에서도 곱셈 오버플로 없이 빈 페이지로 제한한다.
        int from = (int) Math.min((long) page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        // 필터 결과의 페이지를 먼저 확정해 점수·콜·조치 조회가 전체 사건 수에 비례하지 않게 한다.
        List<FdsHoldListPage.FdsHoldListItem> items =
                filtered.subList(from, to).stream().map(this::item).toList();
        // 성공 감사도 목록 조회와 원자적으로 남기기 위해 쓰기 가능한 같은 트랜잭션을 사용한다.
        audit.recordPersonalDataAccess(
                adminId,
                PersonalDataAccessEventType.FDS_HOLD_LIST_VIEW,
                requestIp,
                true,
                Map.of("page", page, "size", size),
                reason);
        return new FdsHoldListPage(
                items, page, size, filtered.size(), filtered.isEmpty() ? 0 : (filtered.size() + size - 1) / size);
    }

    private Candidate candidate(long holdId) {
        TransferHoldForFds hold = holds.findForFds(holdId);
        var job = jobs.findByHoldId(holdId);
        var risk = risks.check(hold.recipientBankCode(), hold.depositAccountNumber());
        return new Candidate(hold, job == null ? null : job.status(), risk);
    }

    private FdsHoldListPage.FdsHoldListItem item(Candidate candidate) {
        TransferHoldForFds hold = candidate.hold();
        long holdId = hold.transferHoldId();
        var score = scores.find(holdId);
        var latestCall = reads.findLatestCallRecord(holdId);
        var action = reads.findLatestAction(holdId);
        String path = path(hold.status(), candidate.risk().status(), action == null ? null : action.actionStatus());
        return new FdsHoldListPage.FdsHoldListItem(
                holdId,
                hold.heldAt(),
                hold.amount(),
                hold.recipientBankCode(),
                mask(hold.depositAccountNumber()),
                hold.policyCode(),
                hold.status(),
                candidate.investigationStatus(),
                score == null ? "PENDING" : score.status(),
                candidate.risk().status(),
                candidate.risk().riskAccountId(),
                candidate.risk().datasetVersion(),
                latestCall == null ? null : latestCall.callResult(),
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

    private record Candidate(TransferHoldForFds hold, String investigationStatus, FdsRiskAccountService.Check risk) {}
}
