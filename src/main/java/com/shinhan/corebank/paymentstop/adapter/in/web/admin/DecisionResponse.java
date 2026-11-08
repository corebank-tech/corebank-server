package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import java.time.LocalDateTime;

/**
 * 판정. decidedBy 와 decidedAt 은 작성자와 작성 시각이고, 수용과 일부 지급정지의 확정 시각은 approvedAt 이다. 보류액은 확정된 일부
 * 지급정지만 갖는다. discretionary 는 담당자 재량 판정인지(ADR-0011), discretionaryReason 은 재량 사유(재량이 아니면 null),
 * gateBranchAtWrite 는 작성 시점 게이트 갈래(이 기능 전에 쓴 판정은 null)다.
 */
public record DecisionResponse(
        long decisionId,
        String result,
        String status,
        String reason,
        String earlyReleaseBasis,
        Long requestedHoldAmount,
        Long appliedHoldAmount,
        long decidedBy,
        LocalDateTime decidedAt,
        Long approvedBy,
        LocalDateTime approvedAt,
        String returnReason,
        boolean discretionary,
        String discretionaryReason,
        String gateBranchAtWrite) {

    static DecisionResponse from(ObjectionDecision decision) {
        return new DecisionResponse(
                decision.decisionId(),
                decision.result().name(),
                decision.status().name(),
                decision.reason(),
                decision.earlyReleaseBasis(),
                decision.requestedHoldAmount(),
                decision.appliedHoldAmount(),
                decision.decidedBy(),
                decision.decidedAt(),
                decision.approvedBy(),
                decision.approvedAt(),
                decision.returnReason(),
                decision.discretionary(),
                decision.discretionaryReason(),
                decision.gateBranchAtWrite() == null
                        ? null
                        : decision.gateBranchAtWrite().name());
    }
}
