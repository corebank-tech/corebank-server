package com.shinhan.corebank.autotransfer.application.port.in;

// 목록 상단 정상처리 건수·금액, 오류처리 건수·금액
public record AutoTransferExecutionHistorySummary(
        long successCount, long successAmount, long errorCount, long errorAmount) {}
