package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
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
}
