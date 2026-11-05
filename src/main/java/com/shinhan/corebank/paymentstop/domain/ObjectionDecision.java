package com.shinhan.corebank.paymentstop.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.paymentstop.domain.exception.PaymentStopErrorCode;
import java.time.LocalDateTime;

/**
 * 담당자가 확정한 판정. 수용과 일부 지급정지는 법상 조기 해제라 근거가 필수다(D29).
 *
 * @param requestedHoldAmount 일부 지급정지 요청 보류액. PARTIAL 이 아니면 null
 * @param appliedHoldAmount 실제로 묶은 보류액. PARTIAL 이 아니면 null
 */
public record ObjectionDecision(
        long objectionId,
        DecisionResult result,
        String reason,
        String earlyReleaseBasis,
        Long requestedHoldAmount,
        Long appliedHoldAmount,
        long decidedBy,
        LocalDateTime decidedAt) {

    public ObjectionDecision {
        if (result == null || reason == null || reason.isBlank() || decidedAt == null) {
            throw new IllegalArgumentException("판정 결과, 사유, 시각은 필수입니다.");
        }
        if (result != DecisionResult.REJECTED && (earlyReleaseBasis == null || earlyReleaseBasis.isBlank())) {
            throw new BusinessException(PaymentStopErrorCode.EARLY_RELEASE_BASIS_REQUIRED);
        }
        if ((result == DecisionResult.PARTIAL) != (requestedHoldAmount != null && appliedHoldAmount != null)) {
            throw new IllegalArgumentException("일부 지급정지만 보류액을 가집니다.");
        }
    }

    // 금액은 아직 모르는 단계에서 근거 요건만 먼저 본다. 계좌를 건드리기 전에 막으려는 것이다
    public static void requireBasis(DecisionResult result, String earlyReleaseBasis) {
        if (result != DecisionResult.REJECTED && (earlyReleaseBasis == null || earlyReleaseBasis.isBlank())) {
            throw new BusinessException(PaymentStopErrorCode.EARLY_RELEASE_BASIS_REQUIRED);
        }
    }
}
