package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
import java.time.LocalDateTime;

public record DecisionResponse(
        String result,
        String reason,
        String earlyReleaseBasis,
        Long requestedHoldAmount,
        Long appliedHoldAmount,
        long decidedBy,
        LocalDateTime decidedAt) {

    static DecisionResponse from(ObjectionDecision decision) {
        return new DecisionResponse(
                decision.result().name(),
                decision.reason(),
                decision.earlyReleaseBasis(),
                decision.requestedHoldAmount(),
                decision.appliedHoldAmount(),
                decision.decidedBy(),
                decision.decidedAt());
    }
}
