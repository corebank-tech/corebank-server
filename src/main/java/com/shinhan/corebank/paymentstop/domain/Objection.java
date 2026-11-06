package com.shinhan.corebank.paymentstop.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;

/** 명의인의 지급정지 이의제기. 사유 조항이 해제 범위를 정한다(D28). */
@Getter
public class Objection {

    private final Long objectionId;
    private final long caseId;
    private final int clause;
    private final String statement;
    private final boolean simplifiedRequested;
    private final boolean idCopyConfirmed;
    private final boolean litigationPending;
    private ObjectionStatus status;
    private final long receivedBy;
    private final LocalDateTime receivedAt;

    private Objection(
            Long objectionId,
            long caseId,
            int clause,
            String statement,
            boolean simplifiedRequested,
            boolean idCopyConfirmed,
            boolean litigationPending,
            ObjectionStatus status,
            long receivedBy,
            LocalDateTime receivedAt) {
        if (clause < 1 || clause > 3) {
            throw new IllegalArgumentException("사유 조항은 1~3호입니다.");
        }
        this.objectionId = objectionId;
        this.caseId = caseId;
        this.clause = clause;
        this.statement = statement;
        this.simplifiedRequested = simplifiedRequested;
        this.idCopyConfirmed = idCopyConfirmed;
        this.litigationPending = litigationPending;
        this.status = status;
        this.receivedBy = receivedBy;
        this.receivedAt = receivedAt;
    }

    /**
     * 접수 요건(D31)을 보고 이의제기를 받는다. 기한이 지났으면 받지 않고, 신분증 사본이나 사유가 없으면 받지 않는다. 보완 요청 기록은
     * PA-10에서 더한다.
     */
    public static Objection receive(
            PaymentStopCase paymentStopCase,
            int clause,
            String statement,
            boolean simplifiedRequested,
            boolean idCopyConfirmed,
            boolean litigationPending,
            long receivedBy,
            LocalDateTime receivedAt) {
        LocalDate today = receivedAt.toLocalDate();
        if (today.isAfter(paymentStopCase.getObjectionDeadline())) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_DEADLINE_PASSED);
        }
        if (!idCopyConfirmed || statement == null || statement.isBlank()) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_REQUIREMENTS_MISSING);
        }
        return new Objection(
                null,
                paymentStopCase.getCaseId(),
                clause,
                statement.strip(),
                simplifiedRequested,
                true,
                litigationPending,
                ObjectionStatus.RECEIVED,
                receivedBy,
                receivedAt);
    }

    public static Objection reconstitute(
            Long objectionId,
            long caseId,
            int clause,
            String statement,
            boolean simplifiedRequested,
            boolean idCopyConfirmed,
            boolean litigationPending,
            ObjectionStatus status,
            long receivedBy,
            LocalDateTime receivedAt) {
        return new Objection(
                objectionId,
                caseId,
                clause,
                statement,
                simplifiedRequested,
                idCopyConfirmed,
                litigationPending,
                status,
                receivedBy,
                receivedAt);
    }

    public void decide() {
        if (status != ObjectionStatus.RECEIVED) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_ALREADY_DECIDED);
        }
        this.status = ObjectionStatus.DECIDED;
    }
}
