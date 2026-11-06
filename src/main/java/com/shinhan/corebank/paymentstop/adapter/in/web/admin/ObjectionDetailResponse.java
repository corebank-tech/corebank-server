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
 * 이의제기 상세. 해제 요건 점검(gate)은 조회 시점 원장으로 다시 계산한 값이다. allowedResults 는 담당자가 고를 수 있는 판정이고,
 * 화면은 막힌 판정의 버튼을 잠그고 규칙별 결과로 이유를 보여 준다.
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
        Gate gate,
        DecisionResponse decision) {

    public record DisputedTransferItem(String transactionNumber, long amount, LocalDateTime occurredAt) {}

    public record Gate(
            String branch,
            boolean simplifiedExcluded,
            String rulesVersion,
            Map<String, Boolean> allowedResults,
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
                new Gate(
                        gate.branch().name(),
                        gate.simplifiedExcluded(),
                        gate.rulesVersion(),
                        Arrays.stream(DecisionResult.values())
                                .collect(Collectors.toMap(
                                        DecisionResult::name, gate::allowsRelease, (a, b) -> a, LinkedHashMap::new)),
                        gate.rules().stream()
                                .map(r -> new Rule(
                                        r.rule().name(),
                                        r.rule().description(),
                                        r.rule().basis().name(),
                                        r.matched(),
                                        r.usedTransactionNumbers(),
                                        r.detail()))
                                .toList()),
                detail.decision().map(DecisionResponse::from).orElse(null));
    }
}
