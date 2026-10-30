package com.shinhan.corebank.common.audit;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    // 적재 대상: [from, to) 사이에 요청된 행 중 해시가 있는 행. ix_audit_requested_at은 뒤에 PK가 붙어
    // (requested_at, audit_log_id) 순서라, 정렬을 그 순서에 맞춰 filesort를 피한다(하루치 실측 12ms → 5.5ms)
    @Query("SELECT a FROM AuditLogJpaEntity a WHERE a.requestedAt >= :from AND a.requestedAt < :to"
            + " AND a.rowHash IS NOT NULL ORDER BY a.requestedAt, a.auditLogId")
    List<AuditLogJpaEntity> findArchiveRowsOfDay(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
