package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferLookupJpaRepository extends JpaRepository<TransferLookupJpaEntity, Long> {
    @Query(
            """
            select t
            from TransferLookupJpaEntity t
            where t.sourceType = :sourceType
            and t.sourceId = :sourceId
            and t.transferredAt >= :startOfDay
            and t.transferredAt < :endOfDay
            order by t.transferredAt desc
            """)
    List<TransferLookupJpaEntity> findBySourceAndDate(
            @Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);
}
