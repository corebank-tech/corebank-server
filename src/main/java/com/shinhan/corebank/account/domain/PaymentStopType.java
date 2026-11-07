package com.shinhan.corebank.account.domain;

/** 지급정지 종류. 전부 지급정지는 계좌를 SUSPENDED로, 일부 지급정지는 보류액만 묶는다(glossary 13번). */
public enum PaymentStopType {
    FULL,
    PARTIAL
}
