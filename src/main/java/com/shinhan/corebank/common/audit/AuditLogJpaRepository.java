package com.shinhan.corebank.common.audit;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    // 적재 대상: [from, to) 사이에 요청된 행 중 해시가 있는 행
    List<AuditLogJpaEntity> findByRequestedAtGreaterThanEqualAndRequestedAtLessThanAndRowHashIsNotNullOrderByAuditLogId(
            LocalDateTime from, LocalDateTime to);
}
