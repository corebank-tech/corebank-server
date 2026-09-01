package com.shinhan.corebank.transfer.application.port.out;

/**
 * 이체 실행 1차 검증(락 이전)에 쓰는 출금계좌 소유·등록 여부 스냅샷.
 * 상태(status)는 여기서 다루지 않는다 — 정지/해지 여부는 lockForTransfer 이후 재검증하는
 * LockedAccount.status()가 최신 스냅샷을 보장하므로 이중으로 조회할 필요가 없다.
 */
public record WithdrawalAccountDetail(Long accountId, Long customerId, boolean withdrawalRegistered) {}
