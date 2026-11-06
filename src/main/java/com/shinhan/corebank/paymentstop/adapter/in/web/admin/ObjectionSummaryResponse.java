package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ObjectionSummaryResponse(
        long objectionId,
        long caseId,
        long accountId,
        int clause,
        String objectionStatus,
        String caseStatus,
        long disputedTotal,
        LocalDate objectionDeadline,
        LocalDateTime receivedAt) {

    static ObjectionSummaryResponse from(ObjectionQueryUseCase.Summary summary) {
        var objection = summary.objection();
        var paymentStopCase = summary.paymentStopCase();
        return new ObjectionSummaryResponse(
                objection.getObjectionId(),
                paymentStopCase.getCaseId(),
                paymentStopCase.getAccountId(),
                objection.getClause(),
                objection.getStatus().name(),
                paymentStopCase.getStatus().name(),
                paymentStopCase.disputedTotal(),
                paymentStopCase.getObjectionDeadline(),
                objection.getReceivedAt());
    }
}
