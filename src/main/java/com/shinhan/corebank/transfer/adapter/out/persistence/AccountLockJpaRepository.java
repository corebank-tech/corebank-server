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
}
