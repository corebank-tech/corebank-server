package com.shinhan.corebank.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_integrity_mismatch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditIntegrityMismatchJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_integrity_mismatch_id")
    private Long auditIntegrityMismatchId;

    @Column(name = "archive_date", nullable = false)
    private LocalDate archiveDate;

    @Column(name = "audit_log_id", nullable = false)
    private Long auditLogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mismatch_type", nullable = false, length = 20)
    private AuditIntegrityMismatchType mismatchType;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    public static AuditIntegrityMismatchJpaEntity of(
            LocalDate archiveDate, Long auditLogId, AuditIntegrityMismatchType mismatchType, LocalDateTime detectedAt) {
        AuditIntegrityMismatchJpaEntity e = new AuditIntegrityMismatchJpaEntity();
        e.archiveDate = archiveDate;
        e.auditLogId = auditLogId;
        e.mismatchType = mismatchType;
        e.detectedAt = detectedAt;
        return e;
    }
}
