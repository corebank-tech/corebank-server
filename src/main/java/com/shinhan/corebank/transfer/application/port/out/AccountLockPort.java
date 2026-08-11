package com.shinhan.corebank.transfer.application.port.out;

public interface AccountLockPort {

    /**
     * 출금/입금 계좌를 계좌 ID 오름차순으로 비관적 락(SELECT FOR UPDATE) 획득한다.
     * 데드락 방지를 위해 락 획득 순서는 항상 ID 오름차순이며, 반환값의 출금/입금
     * 방향은 파라미터로 전달된 역할을 그대로 보존한다.
     */
    LockedAccountsForTransfer lockForTransfer(Long withdrawalAccountId, Long depositAccountId);
}
