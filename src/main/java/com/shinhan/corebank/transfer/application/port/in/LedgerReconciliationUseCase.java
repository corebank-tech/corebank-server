package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDate;
import java.util.List;

// 지정일에 기표가 발생한 계좌만 골라 원장 누적 합계와 계좌 잔액을 대조한다(#378). 탐지 전용 — 자동 정정 없음.
public interface LedgerReconciliationUseCase {

    List<LedgerReconciliationMismatch> reconcile(LocalDate date);
}
