package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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

    // PROCESSING -> 최종 상태(SUCCESS/ERROR) 조건부 UPDATE. 영향받은 행이 1이면 확정 성공,
    // 0이면 이미 다른 재확정 실행이 먼저 이 건을 확정한 것(중복 재확정 방어).
    @Modifying
    @Query(
            value = "UPDATE auto_transfer_execution SET status = :status, transaction_number = :transactionNumber, "
                    + "failure_reason = :failureReason "
                    + "WHERE execution_id = :executionId AND status = 'PROCESSING'",
            nativeQuery = true)
    int finalizeIfProcessing(
            @Param("executionId") Long executionId,
            @Param("status") String status,
            @Param("transactionNumber") String transactionNumber,
            @Param("failureReason") String failureReason);
}
