package com.shinhan.corebank.account.api;

/**
 * 연결 자동·예약이체 등록과 계좌 종료가 동일한 계좌 행 잠금을 사용하도록 한다.
 * 잠금은 호출자의 트랜잭션이 종료될 때 해제된다.
 */
public interface AccountTransferLifecycleLockUseCase {

    void lockForRegistration(Long customerId, Long withdrawalAccountId, String depositAccountNumber);

    void lockForEarlyTermination(Long customerId, Long productAccountId, Long destinationAccountId);
}
