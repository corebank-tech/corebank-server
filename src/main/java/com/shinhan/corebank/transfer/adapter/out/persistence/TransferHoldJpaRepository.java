package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TransferHoldJpaRepository extends JpaRepository<TransferHoldJpaEntity, Long> {

    // ix_transfer_hold_status_release(status, release_at)를 탄다
    @Query(
            """
            SELECT h.transferHoldId FROM TransferHoldJpaEntity h
             WHERE h.status = com.shinhan.corebank.transfer.domain.TransferHoldStatus.HELD
               AND h.releaseMode = com.shinhan.corebank.transfer.api.TransferHoldReleaseMode.AT_TIME
               AND h.releaseAt <= :now
             ORDER BY h.releaseAt
            """)
    List<Long> findReleasableIds(@Param("now") LocalDateTime now, Limit limit);

    @Query(
            """
            SELECT h.transferHoldId FROM TransferHoldJpaEntity h
             WHERE h.status = com.shinhan.corebank.transfer.domain.TransferHoldStatus.EXECUTING
               AND h.executingAt <= :executingBefore
             ORDER BY h.executingAt
            """)
    List<Long> findStuckExecutingIds(@Param("executingBefore") LocalDateTime executingBefore, Limit limit);

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
                   h.reasonCode = :errorCode,
                   h.completedAt = :completedAt
             WHERE h.transferHoldId = :transferHoldId
               AND h.status = com.shinhan.corebank.transfer.domain.TransferHoldStatus.EXECUTING
            """)
    int finishExecution(
            @Param("transferHoldId") long transferHoldId,
            @Param("result") TransferHoldStatus result,
            @Param("transferId") Long transferId,
            @Param("errorCode") String errorCode,
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
