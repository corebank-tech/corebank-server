package com.shinhan.corebank.autotransfer.application.port.in;

import org.springframework.data.domain.Page;

// 실행 이력 목록 + 상단 집계를 합친 조회 결과 전체
public record AutoTransferExecutionHistoryResult(
        Page<AutoTransferExecutionHistoryItem> page,
        AutoTransferExecutionHistorySummary summary
) {
}
