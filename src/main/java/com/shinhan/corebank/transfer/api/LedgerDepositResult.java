package com.shinhan.corebank.transfer.api;

import java.time.LocalDate;

/**
 * @param transactionNumber   원장 거래번호. 호출자는 자기 이력에 저장해 원장과 잇는다
 * @param depositBalanceAfter 돈이 들어간 계좌의 기표 직후 잔액
 * @param tradeDate           기표에 쓴 거래일. 호출자가 거래일을 저장할 때는 today() 가 아니라 이 값을 쓴다
 */
public record LedgerDepositResult(String transactionNumber, long depositBalanceAfter, LocalDate tradeDate) {}
