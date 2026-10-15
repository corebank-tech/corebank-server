package com.shinhan.corebank.transfer.api;

// 이자·만기는 COB가 일으키므로 BT, 고객이 요청한 중도해지는 WB다.
public enum DepositChannel {
    WB,
    BT
}
