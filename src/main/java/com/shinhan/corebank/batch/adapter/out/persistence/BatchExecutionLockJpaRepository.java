package com.shinhan.corebank.batch.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BatchExecutionLockJpaRepository extends JpaRepository<BatchExecutionLockJpaEntity, String> {

    // Fineract와 동일한 패턴 - 짧은 트랜잭션 안에서 행을 잠그고 플래그를 확인·갱신한 뒤 즉시 커밋해서 락을 반납
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM BatchExecutionLockJpaEntity l WHERE l.jobName = :jobName")
    Optional<BatchExecutionLockJpaEntity> findByJobNameForUpdate(@Param("jobName") String jobName);
}
