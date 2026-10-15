package com.shinhan.corebank.transfer.api;

// 입금 계약이 원장 transaction_type에 남기는 업무 구분.
public enum DepositType {
    MATURITY,
    TERMINATION,
    INTEREST
}
