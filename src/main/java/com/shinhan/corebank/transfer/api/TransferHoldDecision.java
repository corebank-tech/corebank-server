package com.shinhan.corebank.transfer.api;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDateTime;

/**
 * 훅 C 결과. 생성은 정적 팩터리로만 한다.
 *
 * @param hold        보류하면 true
 * @param policyCode  보류를 정한 정책 코드. 바로 실행이면 null
 * @param releaseMode 푸는 방식. 바로 실행이면 null
 * @param releaseAt   AT_TIME일 때 실행할 시각. 그 밖에는 null
 */
public record TransferHoldDecision(
        boolean hold, String policyCode, TransferHoldReleaseMode releaseMode, LocalDateTime releaseAt) {

    private static final TransferHoldDecision PROCEED = new TransferHoldDecision(false, null, null, null);

    // 구현체가 값을 잘못 조합한 건 고객 입력 오류가 아니라 내부 결함이라 INTERNAL_ERROR로 막는다
    public TransferHoldDecision {
        boolean valid = hold
                ? policyCode != null
                        && !policyCode.isBlank()
                        && releaseMode != null
                        && (releaseMode == TransferHoldReleaseMode.AT_TIME) == (releaseAt != null)
                : policyCode == null && releaseMode == null && releaseAt == null;
        if (!valid) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR);
        }
    }

    public static TransferHoldDecision proceed() {
        return PROCEED;
    }

    public static TransferHoldDecision holdUntil(String policyCode, LocalDateTime releaseAt) {
        return new TransferHoldDecision(true, policyCode, TransferHoldReleaseMode.AT_TIME, releaseAt);
    }

    public static TransferHoldDecision holdForApproval(String policyCode) {
        return new TransferHoldDecision(true, policyCode, TransferHoldReleaseMode.ON_APPROVAL, null);
    }
}
