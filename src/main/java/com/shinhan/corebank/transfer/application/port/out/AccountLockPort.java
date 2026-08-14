package com.shinhan.corebank.transfer.application.port.out;

import java.util.Optional;

public interface AccountLockPort {

    /**
     * 입금계좌번호로 계좌 ID와 예금주명(거래 시점 스냅샷용)을 조회한다. 락을 잡지 않는 단순
     * 조회이며, 반환된 계좌 ID로 이후 반드시 {@link #lockForTransfer}를 호출해 오름차순 락과
     * 최신 잔액 스냅샷을 얻어야 한다. 이 메서드에서 먼저 락을 잡으면 lockForTransfer의
     * 오름차순 락 계약이 깨져 반대 방향 동시 이체(A→B, B→A) 시 데드락이 재발할 수 있다.
     */
    Optional<ResolvedPayee> resolvePayeeByAccountNumber(String accountNumber);

    /**
     * 출금/입금 계좌를 계좌 ID 오름차순으로 비관적 락(SELECT FOR UPDATE) 획득한다.
     * 데드락 방지를 위해 락 획득 순서는 항상 ID 오름차순이며, 반환값의 출금/입금
     * 방향은 파라미터로 전달된 역할을 그대로 보존한다.
     */
    LockedAccountsForTransfer lockForTransfer(Long withdrawalAccountId, Long depositAccountId);

    /**
     * lockForTransfer로 락을 획득한 출금/입금 계좌에 amount만큼 잔액 변경을 원자적으로
     * 적용하고, 변경 후 잔액을 반환한다.
     * locked는 반드시 lockForTransfer가 반환한 값이어야 하며, 그 호출과 동일 트랜잭션 내에서
     * 실행해야 한다. debit/credit을 별도 메서드로 분리하지 않는 이유: 두 계좌를 하나의 연산으로
     * 묶어야 락 획득 없이 임의의 계좌를 변경하거나, 두 변경을 서로 다른 순서로 호출해
     * 오름차순 락 계약이 깨지는 경로 자체를 막을 수 있다.
     */
    TransferBalances applyTransfer(LockedAccountsForTransfer locked, long amount);
}
