package com.shinhan.corebank.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {
}
