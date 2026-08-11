package com.shinhan.corebank.transfer.application.port.out;

public interface AccountLockPort {

    /**
     * 출금/입금 계좌를 계좌 ID 오름차순으로 비관적 락(SELECT FOR UPDATE) 획득한다.
     * 데드락 방지를 위해 락 획득 순서는 항상 ID 오름차순이며, 반환값의 출금/입금
     * 방향은 파라미터로 전달된 역할을 그대로 보존한다.
     */
    LockedAccountsForTransfer lockForTransfer(Long withdrawalAccountId, Long depositAccountId);

    /** 출금 계좌 잔액을 amount만큼 차감한다. lockForTransfer로 락을 보유한 동일 트랜잭션 내에서 호출해야 한다. */
    void debit(Long accountId, long amount);

    /** 입금 계좌 잔액을 amount만큼 증가시킨다. lockForTransfer로 락을 보유한 동일 트랜잭션 내에서 호출해야 한다. */
    void credit(Long accountId, long amount);
}
