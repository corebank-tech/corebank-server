package com.shinhan.corebank.transfer.application.port.in;

// 페이지 합계가 아니라 조회조건 전체 기준 집계(REQ-CMN-028). 이체 행은 SUCCESS 아니면
// ERROR로만 커밋되므로(#377) 이 두 버킷이 조회 결과 전체를 덮는다.
public record TransferHistorySummary(long successCount, long successAmount, long errorCount, long errorAmount) {}
