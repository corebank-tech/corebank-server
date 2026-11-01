package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TransferHoldJpaRepository extends JpaRepository<TransferHoldJpaEntity, Long> {

    // 모두 이전 상태일 때만 바꾸는 조건부 UPDATE다(1이면 이 호출이 전이). 호출자 트랜잭션이 없으면 따로 커밋한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            """
            UPDATE TransferHoldJpaEntity h
               SET h.status = com.shinhan.corebank.transfer.domain.TransferHoldStatus.EXECUTING,
                   h.actorId = :actorId,
                   h.executingAt = :executingAt
             WHERE h.transferHoldId = :transferHoldId
               AND h.status = com.shinhan.corebank.transfer.domain.TransferHoldStatus.HELD
            """)
    int markExecuting(
            @Param("transferHoldId") long transferHoldId,
            @Param("actorId") Long actorId,
            @Param("executingAt") LocalDateTime executingAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            """
            UPDATE TransferHoldJpaEntity h
               SET h.status = :result,
                   h.transferId = :transferId,
                   h.completedAt = :completedAt
             WHERE h.transferHoldId = :transferHoldId
               AND h.status = com.shinhan.corebank.transfer.domain.TransferHoldStatus.EXECUTING
            """)
    int finishExecution(
            @Param("transferHoldId") long transferHoldId,
            @Param("result") TransferHoldStatus result,
            @Param("transferId") Long transferId,
            @Param("completedAt") LocalDateTime completedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            """
            UPDATE TransferHoldJpaEntity h
               SET h.status = :result,
                   h.actorId = :actorId,
                   h.reasonCode = :reasonCode,
                   h.completedAt = :completedAt
             WHERE h.transferHoldId = :transferHoldId
               AND h.status = com.shinhan.corebank.transfer.domain.TransferHoldStatus.HELD
            """)
    int close(
            @Param("transferHoldId") long transferHoldId,
            @Param("result") TransferHoldStatus result,
            @Param("actorId") Long actorId,
            @Param("reasonCode") String reasonCode,
            @Param("completedAt") LocalDateTime completedAt);
}
