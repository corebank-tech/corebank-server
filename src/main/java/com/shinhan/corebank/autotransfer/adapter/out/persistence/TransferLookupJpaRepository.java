package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TransferLookupJpaRepository extends JpaRepository<TransferLookupJpaEntity, Long> {
    @Query("""
            select t
            from TransferLookupJpaEntity t
            where t.sourceType = :sourceType
            and t.sourceId = :sourceId                                                                             
            and t.transferredAt >= :startOfDay                                                                     
            and t.transferredAt < :endOfDay
            """)
    Optional<TransferLookupJpaEntity> findBySourceAndDate(
            @Param("sourceType") String sourceType,
            @Param("sourceId") Long sourceId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );
}
