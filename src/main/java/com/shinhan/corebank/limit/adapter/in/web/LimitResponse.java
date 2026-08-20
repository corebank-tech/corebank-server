package com.shinhan.corebank.limit.adapter.in.web;

/**
 * 이체한도 조회 응답(REQ-TRSF-024). 금액은 모두 원 단위 정수다.
 */
public record LimitResponse(
        long oneTimeLimit,
        long dailyLimit,
        long dailyUsedAmount,
        long dailyRemainingAmount
) {
}
