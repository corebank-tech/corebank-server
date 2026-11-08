package com.shinhan.corebank.paymentstop.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import com.shinhan.corebank.paymentstop.domain.gate.GateBranch;
import java.time.LocalDateTime;

/**
 * 담당자가 쓴 판정. 수용과 일부 지급정지는 법상 조기 해제라 근거가 필수다(D29). 보류를 줄이는 판정은 작성자와 다른 승인자가 승인해야
 * 확정되고(D13), 불수용은 작성 즉시 확정된다.
 *
 * <p>재량 판정(ADR-0011)은 게이트가 닫은 수용과 일부 지급정지를 담당자가 사유를 남기고 쓴 판정이다. 승인자가 재량임을 확인해야
 * 확정된다.
 *
 * @param decisionId 저장 전이면 null
 * @param requestedHoldAmount 일부 지급정지 요청 보류액. 확정된 PARTIAL 만 갖는다
 * @param appliedHoldAmount 실제로 묶은 보류액. 확정된 PARTIAL 만 갖는다
 * @param decidedBy 작성자
 * @param decidedAt 작성 시각
 * @param discretionary 재량 판정인지. 수용과 일부 지급정지만 가질 수 있다
 * @param discretionaryReason 재량 사유. 재량 판정만 갖는다
 * @param gateBranchAtWrite 작성 시점 게이트 갈래. 이 기능 전에 쓴 판정은 null
 */
public record ObjectionDecision(
        Long decisionId,
        long objectionId,
        DecisionResult result,
        DecisionStatus status,
        String reason,
        String earlyReleaseBasis,
        Long requestedHoldAmount,
        Long appliedHoldAmount,
        long decidedBy,
        LocalDateTime decidedAt,
        Long approvedBy,
        LocalDateTime approvedAt,
        Long returnedBy,
        LocalDateTime returnedAt,
        String returnReason,
        boolean discretionary,
        String discretionaryReason,
        GateBranch gateBranchAtWrite) {

    public static final int DISCRETIONARY_REASON_MIN = 20;
    public static final int DISCRETIONARY_REASON_MAX = 1000;

    public ObjectionDecision {
        if (result == null || status == null || reason == null || reason.isBlank() || decidedAt == null) {
            throw new IllegalArgumentException("판정 결과, 상태, 사유, 시각은 필수입니다.");
        }
        requireBasis(result, earlyReleaseBasis);
        boolean confirmedPartial = result == DecisionResult.PARTIAL && status == DecisionStatus.CONFIRMED;
        if (confirmedPartial != (requestedHoldAmount != null && appliedHoldAmount != null)) {
            throw new IllegalArgumentException("확정된 일부 지급정지만 보류액을 가집니다.");
        }
        if (result == DecisionResult.REJECTED && status != DecisionStatus.CONFIRMED) {
            throw new IllegalArgumentException("불수용은 승인 단계가 없습니다.");
        }
        if (approvedBy != null && approvedBy.longValue() == decidedBy) {
            throw new BusinessException(PaymentStopErrorCode.APPROVER_SAME_AS_AUTHOR);
        }
        if (discretionary
                ? result == DecisionResult.REJECTED || discretionaryReason == null || gateBranchAtWrite == null
                : discretionaryReason != null) {
            throw new IllegalArgumentException("재량 판정은 수용, 일부 지급정지만이고 사유와 작성 시점 갈래가 필요합니다.");
        }
    }

    /** 새 판정. 불수용은 확정, 수용과 일부 지급정지는 승인 대기로 시작한다. 재량이 아니면 재량 사유는 버린다. */
    public static ObjectionDecision write(
            long objectionId,
            DecisionResult result,
            String reason,
            String earlyReleaseBasis,
            boolean discretionary,
            String discretionaryReason,
            GateBranch gateBranchAtWrite,
            long decidedBy,
            LocalDateTime decidedAt) {
        DecisionStatus status =
                result == DecisionResult.REJECTED ? DecisionStatus.CONFIRMED : DecisionStatus.PENDING_APPROVAL;
        return new ObjectionDecision(
                null,
                objectionId,
                result,
                status,
                reason,
                earlyReleaseBasis,
                null,
                null,
                decidedBy,
                decidedAt,
                null,
                null,
                null,
                null,
                null,
                discretionary,
                discretionary && discretionaryReason != null ? discretionaryReason.strip() : null,
                gateBranchAtWrite);
    }

    // 금액은 아직 모르는 단계에서 근거 요건만 먼저 본다. 계좌를 건드리기 전에 막으려는 것이다
    public static void requireBasis(DecisionResult result, String earlyReleaseBasis) {
        if (result != DecisionResult.REJECTED && (earlyReleaseBasis == null || earlyReleaseBasis.isBlank())) {
            throw new BusinessException(PaymentStopErrorCode.EARLY_RELEASE_BASIS_REQUIRED);
        }
    }

    /**
     * 재량 사유 검사. 재량이면 앞뒤 공백을 뺀 20~1000자가 있어야 한다. 불수용은 언제나 열려 있어 재량 표시를 받지 않는다. 계좌를 건드리기
     * 전에 막으려고 금액, 게이트보다 먼저 본다. 어기면 입력 오류(CMN0001)다.
     */
    public static void requireDiscretionaryReason(DecisionResult result, boolean discretionary, String reason) {
        if (!discretionary) {
            return;
        }
        int length = reason == null ? 0 : reason.strip().length();
        if (result == DecisionResult.REJECTED
                || length < DISCRETIONARY_REASON_MIN
                || length > DISCRETIONARY_REASON_MAX) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    /** 재량 판정은 승인자가 재량임을 확인해야 승인된다(PST0311). 일반 판정은 확인 값을 보지 않는다. */
    public void requireDiscretionaryAcknowledged(boolean acknowledged) {
        if (discretionary && !acknowledged) {
            throw new BusinessException(PaymentStopErrorCode.DISCRETIONARY_ACK_REQUIRED);
        }
    }

    public boolean pendingApproval() {
        return status == DecisionStatus.PENDING_APPROVAL;
    }

    /** 승인자 검사. 작성자와 같은 사람은 승인하지 못한다(D13, 내부 기준 D33). */
    public void requireApprover(long approverId) {
        if (approverId == decidedBy) {
            throw new BusinessException(PaymentStopErrorCode.APPROVER_SAME_AS_AUTHOR);
        }
    }

    /** 승인해 확정한 판정. 일부 지급정지는 계좌에 반영한 보류액을 함께 남긴다. */
    public ObjectionDecision approve(long approverId, LocalDateTime at, Long requestedHold, Long appliedHold) {
        if (!pendingApproval()) {
            throw new BusinessException(PaymentStopErrorCode.PENDING_DECISION_NOT_FOUND);
        }
        requireApprover(approverId);
        return new ObjectionDecision(
                decisionId,
                objectionId,
                result,
                DecisionStatus.CONFIRMED,
                reason,
                earlyReleaseBasis,
                requestedHold,
                appliedHold,
                decidedBy,
                decidedAt,
                approverId,
                at,
                null,
                null,
                null,
                discretionary,
                discretionaryReason,
                gateBranchAtWrite);
    }

    public ObjectionDecision returnBy(long returnerId, LocalDateTime at, String returnReason) {
        if (!pendingApproval()) {
            throw new BusinessException(PaymentStopErrorCode.PENDING_DECISION_NOT_FOUND);
        }
        if (returnReason == null || returnReason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 필수입니다.");
        }
        return new ObjectionDecision(
                decisionId,
                objectionId,
                result,
                DecisionStatus.RETURNED,
                reason,
                earlyReleaseBasis,
                null,
                null,
                decidedBy,
                decidedAt,
                null,
                null,
                returnerId,
                at,
                returnReason.strip(),
                discretionary,
                discretionaryReason,
                gateBranchAtWrite);
    }
}
