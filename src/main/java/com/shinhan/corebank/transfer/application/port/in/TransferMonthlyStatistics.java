package com.shinhan.corebank.transfer.application.port.in;

import java.time.YearMonth;

// TransferHistoryQueryPort#summarize를 해당 연월의 월초~월말 기간으로 호출한 결과.
// 별도 집계 쿼리를 새로 만들지 않고 기존 summarize를 재사용해 목록 API 집계값과 항상 일치한다.
public record TransferMonthlyStatistics(
        YearMonth yearMonth, long successCount, long successAmount, long errorCount, long errorAmount) {}
