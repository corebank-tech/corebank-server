package com.shinhan.corebank.common.audit;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditIntegrityMismatchJpaRepository extends JpaRepository<AuditIntegrityMismatchJpaEntity, Long> {

    List<AuditIntegrityMismatchJpaEntity> findByArchiveDate(LocalDate archiveDate);
}
