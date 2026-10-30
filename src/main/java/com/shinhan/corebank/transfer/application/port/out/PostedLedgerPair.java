package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.domain.LedgerEntry;

// 이미 기표된 원장 한 쌍. 정정 체인이 반대기표를 만들 때 원거래 행 ID·시각까지 필요해 저장된 행 그대로 담는다.
public record PostedLedgerPair(LedgerEntry withdrawal, LedgerEntry deposit) {}
