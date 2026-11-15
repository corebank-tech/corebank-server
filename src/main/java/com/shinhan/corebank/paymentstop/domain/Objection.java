package com.shinhan.corebank.paymentstop.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 명의인의 지급정지 이의제기. 사유 조항이 해제 범위를 정한다(D28).
 *
 * <p>상태 전이(PA-10): 접수 → 소명 완비 확인(심사 중) → 판정. 보완 요청은 접수, 심사 중, 보완 제출 상태에서 2회까지 할 수 있고,
 * 명의인이 보완 자료를 내면 담당자가 다시 완비를 확인한다. 수용과 일부 지급정지는 승인 대기를 거쳐 확정되고, 불수용은 바로 확정된다.
 */
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
    private final ObjectionChannel receivedChannel;
    private final LocalDateTime receivedAt;
    private Long completenessConfirmedBy;
    private LocalDateTime completenessConfirmedAt;
    private LocalDate reviewDeadline;
    private int supplementRound;
    private LocalDate supplementDue;

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
            ObjectionChannel receivedChannel,
            LocalDateTime receivedAt,
            Progress progress) {
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
        this.receivedChannel = receivedChannel;
        this.receivedAt = receivedAt;
        this.completenessConfirmedBy = progress.completenessConfirmedBy();
        this.completenessConfirmedAt = progress.completenessConfirmedAt();
        this.reviewDeadline = progress.reviewDeadline();
        this.supplementRound = progress.supplementRound();
        this.supplementDue = progress.supplementDue();
    }

    /**
     * 접수 요건(D31)을 보고 이의제기를 받는다. 기한이 지났으면 받지 않고, 신분증 사본이나 사유가 없으면 받지 않는다. 지급정지 중인 사건만
     * 받는다.
     */
    public static Objection receive(
            PaymentStopCase paymentStopCase,
            int clause,
            String statement,
            boolean simplifiedRequested,
            boolean idCopyConfirmed,
            boolean litigationPending,
            long receivedBy,
            ObjectionChannel channel,
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
                channel,
                receivedAt,
                Progress.initial());
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
            ObjectionChannel receivedChannel,
            LocalDateTime receivedAt,
            Progress progress) {
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
                receivedChannel,
                receivedAt,
                progress);
    }

    /** 소명 완비를 확인하고 심사 기한을 건다. 접수 상태와 보완 제출 상태에서만 한다. 보완 뒤 다시 확인하면 기한을 새로 건다. */
    public void confirmCompleteness(long confirmedBy, LocalDateTime confirmedAt, LocalDate reviewDeadline) {
        if (status != ObjectionStatus.RECEIVED && status != ObjectionStatus.SUPPLEMENT_SUBMITTED) {
            throw new BusinessException(PaymentStopErrorCode.INVALID_OBJECTION_STATE);
        }
        this.status = ObjectionStatus.UNDER_REVIEW;
        this.completenessConfirmedBy = confirmedBy;
        this.completenessConfirmedAt = confirmedAt;
        this.reviewDeadline = reviewDeadline;
    }

    /** 이번 보완 요청의 회차. 확정됐거나 보완을 기다리는 중이면 거부하고, 2회를 넘으면 PST0006 이다. */
    public int nextSupplementRound() {
        requireNotDecided();
        if (status == ObjectionStatus.SUPPLEMENT_REQUESTED || status == ObjectionStatus.PENDING_APPROVAL) {
            throw new BusinessException(PaymentStopErrorCode.INVALID_OBJECTION_STATE);
        }
        if (supplementRound >= ObjectionDeadline.MAX_SUPPLEMENT_ROUNDS) {
            throw new BusinessException(PaymentStopErrorCode.SUPPLEMENT_LIMIT_EXCEEDED);
        }
        return supplementRound + 1;
    }

    /** 보완을 요청한다. 보완 중에는 심사 기한이 멈추고, 보완 뒤 완비를 다시 확인할 때 새로 건다. */
    public void requestSupplement(int round, LocalDate due) {
        if (round != nextSupplementRound()) {
            throw new IllegalStateException("보완 회차가 맞지 않습니다. round=" + round);
        }
        this.status = ObjectionStatus.SUPPLEMENT_REQUESTED;
        this.supplementRound = round;
        this.supplementDue = due;
        this.reviewDeadline = null;
    }

    /** 명의인이 보완 자료를 냈다. 보완 요청 상태에서만 받는다. */
    public void submitSupplement() {
        if (status != ObjectionStatus.SUPPLEMENT_REQUESTED) {
            throw new BusinessException(PaymentStopErrorCode.INVALID_OBJECTION_STATE);
        }
        this.status = ObjectionStatus.SUPPLEMENT_SUBMITTED;
    }

    /**
     * 판정을 쓸 수 있는지 본다. 불수용은 확정 전 언제나 쓸 수 있고(D9, 보완 기한 경과 불수용 D32 포함), 수용과 일부 지급정지는 소명 완비
     * 확인 뒤에만 쓴다. 승인 대기 판정이 있으면 먼저 승인하거나 반려해야 한다.
     */
    public void requireDecidable(DecisionResult result) {
        requireNotDecided();
        if (status == ObjectionStatus.PENDING_APPROVAL) {
            throw new BusinessException(PaymentStopErrorCode.INVALID_OBJECTION_STATE);
        }
        if (result != DecisionResult.REJECTED && status != ObjectionStatus.UNDER_REVIEW) {
            throw new BusinessException(PaymentStopErrorCode.INVALID_OBJECTION_STATE);
        }
    }

    /** 판정을 쓴다. 불수용은 바로 확정, 수용과 일부 지급정지는 승인 대기다. */
    public void writeDecision(DecisionResult result) {
        requireDecidable(result);
        this.status = result == DecisionResult.REJECTED ? ObjectionStatus.DECIDED : ObjectionStatus.PENDING_APPROVAL;
    }

    /** 승인 대기 판정을 승인해 확정한다. */
    public void approve() {
        requirePendingApproval();
        this.status = ObjectionStatus.DECIDED;
    }

    /** 승인 대기 판정을 반려한다. 심사 중으로 돌아가고 심사 기한은 그대로다. */
    public void returnDecision() {
        requirePendingApproval();
        this.status = ObjectionStatus.UNDER_REVIEW;
    }

    public void requireNotDecided() {
        if (status == ObjectionStatus.DECIDED) {
            throw new BusinessException(PaymentStopErrorCode.OBJECTION_ALREADY_DECIDED);
        }
    }

    private void requirePendingApproval() {
        requireNotDecided();
        if (status != ObjectionStatus.PENDING_APPROVAL) {
            throw new BusinessException(PaymentStopErrorCode.PENDING_DECISION_NOT_FOUND);
        }
    }

    public Progress progress() {
        return new Progress(
                completenessConfirmedBy, completenessConfirmedAt, reviewDeadline, supplementRound, supplementDue);
    }

    /** 완비 확인과 기한, 보완 진행. 저장과 복원에 쓴다. */
    public record Progress(
            Long completenessConfirmedBy,
            LocalDateTime completenessConfirmedAt,
            LocalDate reviewDeadline,
            int supplementRound,
            LocalDate supplementDue) {

        public static Progress initial() {
            return new Progress(null, null, null, 0, null);
        }
    }
}
