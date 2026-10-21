package com.shinhan.corebank.gl.domain;

/** 대사에 쓰는 원장 거래 한 건. 금액은 행 하나의 금액이다(2행 합산 아님). */
public record LedgerTransaction(String transactionNumber, String ledgerTransactionType, long amount) {}
