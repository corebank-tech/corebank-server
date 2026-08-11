package com.shinhan.corebank.transfer.application.port.out;

/**
 * 비관적 락 획득 직후의 계좌 스냅샷. account 도메인을 참조하지 않고
 * transfer 실행에 필요한 최소 정보(잔액·상태)만 담는다.
 */
public record LockedAccount(Long accountId, long balance, String status) {
}
