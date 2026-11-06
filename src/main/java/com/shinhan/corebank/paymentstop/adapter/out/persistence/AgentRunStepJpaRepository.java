package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRunStepJpaRepository extends JpaRepository<AgentRunStepJpaEntity, Long> {

    Optional<AgentRunStepJpaEntity> findByRunIdAndSeq(Long runId, Integer seq);

    List<AgentRunStepJpaEntity> findByRunIdAndSeqGreaterThanOrderBySeqAsc(Long runId, Integer seq);

    @Query("select coalesce(max(s.seq), 0) from AgentRunStepJpaEntity s where s.runId = :runId")
    int maxSeq(@Param("runId") Long runId);
}
