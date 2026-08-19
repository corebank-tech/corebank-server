package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AutoTransferExecutionJpaRepository extends JpaRepository<AutoTransferExecutionJpaEntity, Long> {
    @Query("""
            select e
            from AutoTransferExecutionJpaEntity e
            join fetch e.autoTransfer
            where e.status = :status
            """)
    List<AutoTransferExecutionJpaEntity> findAllByStatusWithAutoTransfer(@Param("status")ProcessResultStatus status);

    // 연속 실패 감지용 - 이 자동이체의 실행이력을 최근 실행일 순으로, pageable이 정한 개수만큼만 조회
    @Query("""
            select e
            from AutoTransferExecutionJpaEntity e
            where e.autoTransfer.autoTransferId = :autoTransferId
            order by e.executionDate desc
            """)
    List<AutoTransferExecutionJpaEntity> findRecentByAutoTransferId(
            @Param("autoTransferId") Long autoTransferId,
            Pageable pageable
    );
}
