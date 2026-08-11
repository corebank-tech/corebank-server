package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountLockJpaRepository extends JpaRepository<AccountLockJpaEntity, Long> {

    /**
     * 팀 JPQL 정적 쿼리 컨벤션 및 비관적 락(PESSIMISTIC_WRITE)을 적용하여
     * 계좌 레코드를 조회하고 X-Lock을 획득한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT a
        FROM AccountLockJpaEntity a
        WHERE a.accountId = :accountId
        """)
    Optional<AccountLockJpaEntity> findByAccountIdForUpdate(@Param("accountId") Long accountId);

    /**
     * 입금계좌번호 → ID 해석 전용 조회. 의도적으로 락을 잡지 않는다.
     * 여기서 SELECT FOR UPDATE를 걸면 {@link AccountLockPersistenceAdapter#lockForTransfer}가
     * 보장하는 account_id 오름차순 락 획득 계약보다 먼저 입금계좌 행에 락이 걸려, 두 이체가
     * 반대 방향(A→B, B→A)으로 동시 실행될 때 정확히 그 데드락이 재발한다. 이 메서드는 ID만
     * 얻기 위한 평범한 조회이고, 실제 락은 이후 lockForTransfer가 오름차순으로 잡는다.
     */
    @Query("""
        SELECT a
        FROM AccountLockJpaEntity a
        WHERE a.accountNumber = :accountNumber
        """)
    Optional<AccountLockJpaEntity> findByAccountNumber(@Param("accountNumber") String accountNumber);
}
