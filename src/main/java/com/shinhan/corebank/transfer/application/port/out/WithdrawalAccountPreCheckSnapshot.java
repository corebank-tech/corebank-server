package com.shinhan.corebank.transfer.application.port.out;

/**
 * 출금계좌 잔액·상태 사전 체크(락 없음)용 스냅샷. OTP는 인증 성공 시 즉시 소비되는 자원이라,
 * 계좌 락 획득 전에 통과 못 할 게 뻔한 요청(잔액부족·정지)을 미리 걸러 OTP 낭비를 줄이는 데 쓴다.
 * 락이 없으므로 최종 판정이 아니다 — 최종 권위는 lockForTransfer 이후 재검증이 갖는다.
 */
public record WithdrawalAccountPreCheckSnapshot(LockedAccountStatus status, long balance) {
}
