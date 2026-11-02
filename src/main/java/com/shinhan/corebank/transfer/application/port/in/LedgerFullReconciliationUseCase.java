package com.shinhan.corebank.transfer.application.port.in;

import java.util.List;

// 전 계좌의 원장 누적 합계와 잔액을 대조한다(#468). 원장 기표가 없어 증분 대사(#378)가 보지 못하는
// 휴면 계좌의 드리프트까지 잡는다. 탐지 전용 — 자동 정정 없음.
public interface LedgerFullReconciliationUseCase {

    // 청크 하나가 실패해도 나머지를 끝까지 확인하고, 확인하지 못한 구간을 결과에 담아 돌려준다(#692)
    LedgerFullReconciliationResult reconcileAllIsolatingFailures();

    // 확인하지 못한 구간이 있으면 예외로 알린다 — 구간을 따로 처리하지 않는 호출부용
    default List<LedgerReconciliationMismatch> reconcileAll() {
        LedgerFullReconciliationResult result = reconcileAllIsolatingFailures();
        if (!result.unverifiedRanges().isEmpty()) {
            throw new IllegalStateException("원장-잔액 전수 대사에서 확인하지 못한 구간이 있음 - " + result.unverifiedRanges());
        }
        return result.mismatches();
    }
}
