package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRunJpaRepository extends JpaRepository<AgentRunJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AgentRunJpaEntity r where r.runId = :runId")
    Optional<AgentRunJpaEntity> findByIdForUpdate(@Param("runId") Long runId);

    List<AgentRunJpaEntity> findByObjectionIdOrderByRunIdDesc(Long objectionId);

    Optional<AgentRunJpaEntity> findFirstByObjectionIdOrderByRunIdDesc(Long objectionId);

    @Query(
            """
            select r from AgentRunJpaEntity r
             where r.runId in (select max(r2.runId) from AgentRunJpaEntity r2
                                where r2.objectionId in :objectionIds group by r2.objectionId)
            """)
    List<AgentRunJpaEntity> findLatestByObjectionIds(@Param("objectionIds") Collection<Long> objectionIds);

    // 범위 UPDATE 로 닫으면 상태 인덱스에 범위 잠금이 걸려, 동시에 가져가는 워커의 상태 변경과 데드락이 난다.
    // 그래서 대상 ID 는 잠금 없이 읽고 PK 로 한 건씩 조건부로 닫는다
    @Query(
            """
            select r.runId from AgentRunJpaEntity r
             where r.status = :running and r.leaseUntil < :now and r.attempt >= :maxAttempts
            """)
    List<Long> findExhaustedIds(
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") int maxAttempts,
            @Param("running") AgentRunStatus running);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update AgentRunJpaEntity r
               set r.status = :failed, r.leaseUntil = null, r.endedAt = :now, r.failureReason = :reason
             where r.runId = :runId and r.status = :running and r.leaseUntil < :now and r.attempt >= :maxAttempts
            """)
    int failExhaustedById(
            @Param("runId") Long runId,
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") int maxAttempts,
            @Param("reason") String reason,
            @Param("running") AgentRunStatus running,
            @Param("failed") AgentRunStatus failed);

    @Query(
            """
            select r.runId from AgentRunJpaEntity r
             where r.attempt < :maxAttempts
               and (r.status = :queued or (r.status = :running and r.leaseUntil < :now))
             order by r.queuedAt asc, r.runId asc
            """)
    List<Long> findClaimCandidates(
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") int maxAttempts,
            @Param("queued") AgentRunStatus queued,
            @Param("running") AgentRunStatus running,
            Pageable pageable);

    // 조회 후 갱신 경쟁을 WHERE 절 조건으로 막는다. 먼저 바꾼 쪽만 1행을 얻는다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update AgentRunJpaEntity r
               set r.status = :running, r.attempt = r.attempt + 1, r.leaseUntil = :leaseUntil,
                   r.startedAt = coalesce(r.startedAt, :now)
             where r.runId = :runId and r.attempt < :maxAttempts
               and (r.status = :queued or (r.status = :running and r.leaseUntil < :now))
            """)
    int tryClaim(
            @Param("runId") Long runId,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("maxAttempts") int maxAttempts,
            @Param("queued") AgentRunStatus queued,
            @Param("running") AgentRunStatus running);
}
