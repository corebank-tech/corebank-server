package com.shinhan.corebank.transfer.domain;

public enum TransferChannel {
    // 인터넷뱅킹 (고객 직접 이체)
    WB,

    // 배치 (예약/자동이체 등 시스템 구동)
    BT
}
