package com.shinhan.corebank.common.audit;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditArchiveExecutionJpaRepository extends JpaRepository<AuditArchiveExecutionJpaEntity, Long> {

    Optional<AuditArchiveExecutionJpaEntity> findByArchiveDateAndArchiveType(
            LocalDate archiveDate, AuditArchivePath archiveType);
}
