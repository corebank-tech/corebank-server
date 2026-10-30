package com.shinhan.corebank.transfer.application.port.in;

// 대사 배치(#378)가 탐지한 불일치 1건 — 원장 누적 합계와 계좌 잔액이 다른 계좌.
public record LedgerReconciliationMismatch(Long accountId, long ledgerBalance, long accountBalance) {}
