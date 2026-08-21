package com.shinhan.corebank.limit.application.port.in.dto;

import com.shinhan.corebank.limit.domain.TransferLimit;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;

import lombok.Builder;

/**
 * 한도 조회 결과(REQ-TRSF-024). 금액은 모두 원 단위 정수다.
 */
@Builder
public record LimitResult(
        long oneTimeLimit,
        long dailyLimit,
        long dailyUsedAmount,
        long dailyRemainingAmount
) {

    public static LimitResult from(TransferLimit limit, TransferLimitDailyUsage usage) {
        return LimitResult.builder()
                .oneTimeLimit(limit.getOneTimeLimit())
                .dailyLimit(limit.getDailyLimit())
                .dailyUsedAmount(usage.getUsedAmount())
                .dailyRemainingAmount(usage.remainingAgainst(limit.getDailyLimit()))
                .build();
    }
}
