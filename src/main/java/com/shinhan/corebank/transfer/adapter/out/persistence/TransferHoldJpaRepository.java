package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.domain.TransferHoldStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TransferHoldJpaRepository extends JpaRepository<TransferHoldJpaEntity, Long> {

    // 기표 트랜잭션 안에서 부른다. SELECT ... FOR UPDATE라 커밋까지 재선점·결과 기록이 기다린다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT h FROM TransferHoldJpaEntity h
             WHERE h.transferHoldId = :transferHoldId
               AND h.status = com.shinhan.corebank.transfer.domain.TransferHoldStatus.EXECUTING
               AND h.executingAt = :claimedAt
            """)
    Optional<TransferHoldJpaEntity> findClaimForUpdate(
            @Param("transferHoldId") long transferHoldId, @Param("claimedAt") LocalDateTime claimedAt);

    // 모두 이전 상태일 때만 바꾸는 조건부 UPDATE다(1이면 이 호출이 전이). 호출자 트랜잭션이 없으면 따로 커밋한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            """
            UPDATE TransferHoldJpaEntity h
               SET h.status = com.shinhan.corebank.transfer.domain.TransferHoldStatus.EXECUTING,
                   h.executingAt = :executingAt
             WHERE h.transferHoldId = :transferHoldId
               AND h.status = com.shinhan.corebank.transfer.domain.TransferHoldStatus.HELD
               AND h.releaseMode = com.shinhan.corebank.transfer.api.TransferHoldReleaseMode.AT_TIME
               AND h.releaseAt <= :executingAt
            """)
    int markDueExecuting(@Param("transferHoldId") long transferHoldId, @Param("executingAt") LocalDateTime executingAt);

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
               AND h.releaseMode = com.shinhan.corebank.transfer.api.TransferHoldReleaseMode.ON_APPROVAL
            """)
    int markApprovedExecuting(
            @Param("transferHoldId") long transferHoldId,
            @Param("actorId") long actorId,
            @Param("executingAt") LocalDateTime executingAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
            """
            UPDATE TransferHoldJpaEntity h
               SET h.executingAt = :executingAt
             WHERE h.transferHoldId = :transferHoldId
               AND h.status = com.shinhan.corebank.transfer.domain.TransferHoldStatus.EXECUTING
               AND h.executingAt <= :staleBefore
            """)
    int reclaimStuck(
            @Param("transferHoldId") long transferHoldId,
            @Param("staleBefore") LocalDateTime staleBefore,
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
               AND h.executingAt = :claimedAt
            """)
    int finishExecution(
            @Param("transferHoldId") long transferHoldId,
            @Param("claimedAt") LocalDateTime claimedAt,
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
