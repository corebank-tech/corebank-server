package com.shinhan.corebank.limit.application.port.in.dto;

import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;

/**
 * 한도 조회 결과(REQ-TRSF-024). 금액은 모두 원 단위 정수다.
 */
public record LimitResult(
        long oneTimeLimit,
        long dailyLimit,
        long dailyUsedAmount,
        long dailyRemainingAmount
) {

    public static LimitResult from(TransferLimit limit, TransferLimitDailyUsage usage) {
        return new LimitResult(
                limit.getOneTimeLimit(),
                limit.getDailyLimit(),
                usage.getUsedAmount(),
                usage.remainingAgainst(limit.getDailyLimit()));
    }
}
