package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 이의제기 목록 행. 분석 실행 상태와 마지막 단계 제목을 함께 줘서 화면이 행마다 실행을 따로 부르지 않게 한다. supplementOverdue 는 보완
 * 기한이 지난 불수용 대상이다(D32).
 */
public record ObjectionSummaryResponse(
        long objectionId,
        long caseId,
        long accountId,
        int clause,
        String objectionStatus,
        String caseStatus,
        long disputedTotal,
        LocalDate objectionDeadline,
        LocalDateTime receivedAt,
        String receivedChannel,
        LocalDate reviewDeadline,
        boolean deadlineAlert,
        LocalDate supplementDue,
        boolean supplementOverdue,
        String latestRunStatus,
        String latestRunLastStepTitle) {

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
                objection.getReceivedAt(),
                objection.getReceivedChannel().name(),
                objection.getReviewDeadline(),
                summary.flags().deadlineAlert(),
                objection.getSupplementDue(),
                summary.flags().supplementOverdue(),
                summary.latestRunStatus() == null
                        ? null
                        : summary.latestRunStatus().name(),
                summary.latestRunLastStepTitle());
    }
}
