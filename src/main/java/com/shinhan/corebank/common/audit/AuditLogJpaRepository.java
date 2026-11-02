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

    // 검증 대상: 해시를 NULL로 넣은 위조 행도 빠지지 않도록 그날 행을 전부 읽는다
    List<AuditLogJpaEntity> findByRequestedAtGreaterThanEqualAndRequestedAtLessThan(
            LocalDateTime from, LocalDateTime to);
}
