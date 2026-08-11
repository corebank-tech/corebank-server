package com.shinhan.corebank.transfer.application.port.out;

public interface AccountLockPort {

    /**
     * 출금/입금 계좌를 계좌 ID 오름차순으로 비관적 락(SELECT FOR UPDATE) 획득한다.
     * 데드락 방지를 위해 락 획득 순서는 항상 ID 오름차순이며, 반환값의 출금/입금
     * 방향은 파라미터로 전달된 역할을 그대로 보존한다.
     */
    LockedAccountsForTransfer lockForTransfer(Long withdrawalAccountId, Long depositAccountId);

    /**
     * 출금 계좌 잔액을 amount만큼 차감한다.
     * account는 반드시 lockForTransfer가 반환한 값이어야 하며, 그 호출과 동일 트랜잭션 내에서
     * 실행해야 한다. accountId를 직접 받지 않는 이유: lockForTransfer를 거치지 않고 임의의
     * 계좌 ID로 바로 호출하면 오름차순 락 획득이라는 데드락 방지 계약이 깨질 수 있다.
     */
    void debit(LockedAccount account, long amount);

    /**
     * 입금 계좌 잔액을 amount만큼 증가시킨다.
     * account는 반드시 lockForTransfer가 반환한 값이어야 하며, 그 호출과 동일 트랜잭션 내에서
     * 실행해야 한다.
     */
    void credit(LockedAccount account, long amount);
}
