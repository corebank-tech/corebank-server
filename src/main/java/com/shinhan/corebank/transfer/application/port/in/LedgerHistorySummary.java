package com.shinhan.corebank.transfer.application.port.in;

import lombok.Builder;

// 페이지 합계가 아니라 조회조건 전체(요청한 direction 필터 포함) 기준 집계.
@Builder
public record LedgerHistorySummary(
        long depositCount,
        long depositAmount,
        long withdrawalCount,
        long withdrawalAmount
) {
    public static LedgerHistorySummary empty() {
        return new LedgerHistorySummary(0L, 0L, 0L, 0L);
    }
}
