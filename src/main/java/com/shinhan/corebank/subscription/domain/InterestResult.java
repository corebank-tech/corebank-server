package com.shinhan.corebank.subscription.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.math.BigDecimal;

public record InterestResult(long gross, long tax, long net, BigDecimal appliedRate) {

    public InterestResult {
        if (appliedRate == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        if (appliedRate.signum() < 0 || gross < 0 || tax < 0 || tax > gross || net != gross - tax) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
