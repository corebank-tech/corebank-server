package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase;
import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 이의제기 상세. 해제 요건 점검(gate)은 조회 시점 원장과 외부 확인 회신으로 다시 계산한 값이다. allowedResults 는 담당자가 고를 수 있는
 * 판정이고, 화면은 막힌 판정의 버튼을 잠그고 규칙별 결과로 이유를 보여 준다. discretionaryResults 는 게이트가 닫았지만 담당자 재량
 * 판정으로 쓸 수 있는 판정이다(ADR-0011, 불수용은 언제나 false). decision 은 확정된 판정, pendingDecision 은 승인 대기
 * 판정이다(없으면 null).
 */
public record ObjectionDetailResponse(
        long objectionId,
        long caseId,
        long accountId,
        Long senderAccountId,
        String caseStatus,
        LocalDate objectionDeadline,
        List<DisputedTransferItem> disputedTransfers,
        int clause,
        String statement,
        boolean simplifiedRequested,
        boolean idCopyConfirmed,
        boolean litigationPending,
        String objectionStatus,
        LocalDateTime receivedAt,
        String receivedChannel,
        LocalDateTime completenessConfirmedAt,
        LocalDate reviewDeadline,
        boolean deadlineAlert,
        LocalDate supplementDue,
        int supplementRound,
        boolean supplementOverdue,
        List<SupplementRequestItem> supplementRequests,
        Gate gate,
        DecisionResponse decision,
        DecisionResponse pendingDecision,
        List<ExternalCheckResponse> externalChecks,
        int documentCount) {

    public record SupplementRequestItem(
            int round,
            String reason,
            List<String> missingDocuments,
            LocalDate dueDate,
            LocalDateTime requestedAt,
            LocalDateTime submittedAt,
            String submitNote) {}

    public record DisputedTransferItem(String transactionNumber, long amount, LocalDateTime occurredAt) {}

    public record Gate(
            String branch,
            boolean simplifiedExcluded,
            String rulesVersion,
            Map<String, Boolean> allowedResults,
            Map<String, Boolean> discretionaryResults,
            List<Rule> rules) {}

    public record Rule(
            String rule,
            String description,
            String basis,
            boolean matched,
            List<String> transactionNumbers,
            String detail) {}

    static ObjectionDetailResponse from(ObjectionQueryUseCase.Detail detail) {
        var objection = detail.objection();
        var paymentStopCase = detail.paymentStopCase();
        var gate = detail.gate();
        return new ObjectionDetailResponse(
                objection.getObjectionId(),
                paymentStopCase.getCaseId(),
                paymentStopCase.getAccountId(),
                paymentStopCase.getSenderAccountId(),
                paymentStopCase.getStatus().name(),
                paymentStopCase.getObjectionDeadline(),
                paymentStopCase.getDisputedTransfers().stream()
                        .map(t -> new DisputedTransferItem(t.transactionNumber(), t.amount(), t.occurredAt()))
                        .toList(),
                objection.getClause(),
                objection.getStatement(),
                objection.isSimplifiedRequested(),
                objection.isIdCopyConfirmed(),
                objection.isLitigationPending(),
                objection.getStatus().name(),
                objection.getReceivedAt(),
                objection.getReceivedChannel().name(),
                objection.getCompletenessConfirmedAt(),
                objection.getReviewDeadline(),
                detail.flags().deadlineAlert(),
                objection.getSupplementDue(),
                objection.getSupplementRound(),
                detail.flags().supplementOverdue(),
                detail.supplementRequests().stream()
                        .map(r -> new SupplementRequestItem(
                                r.round(),
                                r.reason(),
                                r.missingDocuments(),
                                r.dueDate(),
                                r.requestedAt(),
                                r.submittedAt(),
                                r.submitNote()))
                        .toList(),
                new Gate(
                        gate.branch().name(),
                        gate.simplifiedExcluded(),
                        gate.rulesVersion(),
                        Arrays.stream(DecisionResult.values())
                                .collect(Collectors.toMap(
                                        DecisionResult::name, gate::allowsRelease, (a, b) -> a, LinkedHashMap::new)),
                        Arrays.stream(DecisionResult.values())
                                .collect(Collectors.toMap(
                                        DecisionResult::name,
                                        gate::allowsDiscretionary,
                                        (a, b) -> a,
                                        LinkedHashMap::new)),
                        gate.rules().stream()
                                .map(r -> new Rule(
                                        r.rule().name(),
                                        r.rule().description(),
                                        r.rule().basis().name(),
                                        r.matched(),
                                        r.usedTransactionNumbers(),
                                        r.detail()))
                                .toList()),
                detail.decision().map(DecisionResponse::from).orElse(null),
                detail.pendingDecision().map(DecisionResponse::from).orElse(null),
                detail.externalChecks().stream()
                        .map(ExternalCheckResponse::from)
                        .toList(),
                detail.documentCount());
    }
}
