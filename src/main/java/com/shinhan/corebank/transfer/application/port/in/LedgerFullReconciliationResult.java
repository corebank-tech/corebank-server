package com.shinhan.corebank.transfer.application.port.in;

import java.util.List;

// 전수 대사 결과 — 찾은 불일치와, 청크 실패로 확인하지 못한 구간. 확인 못 한 구간은 "불일치 0건"이 아니다.
public record LedgerFullReconciliationResult(
        List<LedgerReconciliationMismatch> mismatches, List<UnverifiedAccountRange> unverifiedRanges) {}
