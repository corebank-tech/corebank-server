package com.shinhan.corebank.transfer.api;

// 누가 시작했는지로 나눈다. COB가 시작하면 BT, 고객이 요청하면(중도해지·만기해지) WB다.
public enum DepositChannel {
    WB,
    BT
}
