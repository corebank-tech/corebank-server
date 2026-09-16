package com.shinhan.corebank.transfer.application.port.out;

/**
 * 이체 실행을 위해 락을 획득한 출금/입금 계좌 쌍.
 * 내부 락 획득 순서(계좌 ID 오름차순)와 무관하게 이체 방향(출금/입금)을 보존한다.
 */
public record LockedAccountsForTransfer(LockedAccount withdrawal, LockedAccount deposit) {}
