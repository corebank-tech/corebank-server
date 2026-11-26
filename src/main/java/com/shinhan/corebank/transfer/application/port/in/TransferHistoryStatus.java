package com.shinhan.corebank.transfer.application.port.in;

/** 고객 이체결과조회 전용 상태. 실제 이체 상태와 실행 전 보류 상태를 한 계약으로 표현한다. */
public enum TransferHistoryStatus {
    SUCCESS,
    ERROR,
    TIMEOUT,
    HELD,
    REJECTED
}
