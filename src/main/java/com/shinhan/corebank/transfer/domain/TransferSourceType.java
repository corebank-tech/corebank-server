package com.shinhan.corebank.transfer.domain;

public enum TransferSourceType {
    // 예약이체에 의해 발생된 이체
    SCHEDULED,

    // 자동이체에 의해 발생된 이체
    AUTO,

    // 이체 보류를 실행해 발생된 이체. transfer_type은 IMMEDIATE다(#772)
    HELD
}
