package com.shinhan.corebank.transfer.application.port.in;

import java.util.List;

// 전 계좌의 원장 누적 합계와 잔액을 대조한다(#468). 원장 기표가 없어 증분 대사(#378)가 보지 못하는
// 휴면 계좌의 드리프트까지 잡는다. 탐지 전용 — 자동 정정 없음.
public interface LedgerFullReconciliationUseCase {

    List<LedgerReconciliationMismatch> reconcileAll();
}
