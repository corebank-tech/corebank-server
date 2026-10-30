package com.shinhan.corebank.transfer.api;

import java.time.LocalDate;

/**
 * 훅 B 입력.
 *
 * @param transactionNumber 거래번호. 후속 기표의 참조 키로 쓴다
 * @param txType            원장 거래유형(ledger_entry.transaction_type) — IMMEDIATE_TRANSFER · SCHEDULED_TRANSFER ·
 *                          AUTO_TRANSFER · PRODUCT_SUBSCRIPTION · REVERSAL
 * @param amount            이체 금액(원)
 * @param fromAccountId     출금계좌 ID
 * @param toAccountId       입금계좌 ID
 * @param tradeDate         거래일(귀속 영업일). 원장 행의 trade_date와 같은 값이다
 */
public record LedgerPostingContext(
        String transactionNumber,
        String txType,
        long amount,
        long fromAccountId,
        long toAccountId,
        LocalDate tradeDate) {}
