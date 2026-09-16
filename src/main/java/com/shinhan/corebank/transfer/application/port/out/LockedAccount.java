package com.shinhan.corebank.transfer.application.port.out;

/**
 * 비관적 락 획득 직후의 계좌 스냅샷. account 도메인을 참조하지 않고
 * transfer 실행에 필요한 최소 정보(계좌번호·잔액·상태)만 담는다.
 * accountNumber는 락 획득 전 계좌번호 조회 결과가 락 획득 시점까지 유효한지
 * 재검증하는 용도로 쓰인다.
 */
public record LockedAccount(Long accountId, String accountNumber, long balance, LockedAccountStatus status) {}
