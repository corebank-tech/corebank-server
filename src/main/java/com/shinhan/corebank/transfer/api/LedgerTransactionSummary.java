package com.shinhan.corebank.transfer.api;

import java.time.LocalDate;

/**
 * 원장 거래 한 건. 출금·입금 2행 거래도 거래번호 하나로 한 줄이다.
 *
 * @param transactionType 원장 {@code transaction_type} 값 그대로다(예: {@code IMMEDIATE_TRANSFER})
 * @param amount 거래 금액. 행 하나의 금액이고 2행을 합산한 값이 아니다
 */
public record LedgerTransactionSummary(
        String transactionNumber, String transactionType, LocalDate tradeDate, long amount) {}
