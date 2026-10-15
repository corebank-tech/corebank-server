package com.shinhan.corebank.transfer.api;

/**
 * @param transactionNumber   원장 거래번호. 호출자는 자기 이력에 저장해 원장과 잇는다
 * @param depositBalanceAfter 돈이 들어간 계좌의 기표 직후 잔액
 */
public record LedgerDepositResult(String transactionNumber, long depositBalanceAfter) {}
