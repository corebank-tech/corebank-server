package com.shinhan.corebank.transfer.application.port.in;

// 페이지 합계가 아니라 조회조건 전체 기준 집계(REQ-CMN-028). PROCESSING은 목록엔 나오지만
// 아직 확정 안 된 상태라 집계 버킷에는 잡히지 않는다(AutoTransferExecutionHistorySummary·
// ScheduledTransferExecutionResultSummary와 동일 원칙).
public record TransferHistorySummary(
        long successCount,
        long successAmount,
        long errorCount,
        long errorAmount
) {
}
