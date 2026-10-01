package com.shinhan.corebank.subscription.application.port.out;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.subscription.domain.LedgerBalancePoint;
import java.util.List;

public record LedgerBalanceHistory(long openingBalance, List<LedgerBalancePoint> points) {

    public LedgerBalanceHistory {
        if (points == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        points = List.copyOf(points);
    }
}
