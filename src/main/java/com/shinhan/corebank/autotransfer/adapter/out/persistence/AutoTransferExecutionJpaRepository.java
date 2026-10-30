package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface AutoTransferExecutionJpaRepository extends JpaRepository<AutoTransferExecutionJpaEntity, Long> {
    @Query(
            """
            select e
            from AutoTransferExecutionJpaEntity e
            join fetch e.autoTransfer
            where e.status = :status
            """)
    List<AutoTransferExecutionJpaEntity> findAllByStatusWithAutoTransfer(@Param("status") ProcessResultStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AutoTransferExecutionJpaEntity> findForUpdateByExecutionIdAndStatus(
            Long executionId, ProcessResultStatus status);

    // 다른 트랜잭션이 잡고 있는 행은 기다리지 않고 빈 결과로 돌려준다(-2 = SKIP LOCKED)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    Optional<AutoTransferExecutionJpaEntity> findForUpdateSkipLockedByExecutionIdAndStatus(
            Long executionId, ProcessResultStatus status);

    // 연속 실패 감지용 - 이 자동이체의 실행이력을 최근 실행일 순으로, pageable이 정한 개수만큼만 조회
    @Query(
            """
            select e
            from AutoTransferExecutionJpaEntity e
            where e.autoTransfer.autoTransferId = :autoTransferId
            order by e.executionDate desc
            """)
    List<AutoTransferExecutionJpaEntity> findRecentByAutoTransferId(
            @Param("autoTransferId") Long autoTransferId, Pageable pageable);
}
