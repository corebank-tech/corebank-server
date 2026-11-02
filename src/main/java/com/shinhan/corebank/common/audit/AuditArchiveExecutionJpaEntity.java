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

// 감사 로그 WORM 적재의 날짜 · 경로별 완료 상태(PH-82). 사본이 없다는 것만으로는 안 올렸는지 · 실패했는지 · 지워졌는지 모르므로 여기에 남긴다.
@Entity
@Table(name = "audit_archive_execution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditArchiveExecutionJpaEntity {

    private static final int FAILURE_REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_archive_execution_id")
    private Long auditArchiveExecutionId;

    @Column(name = "archive_date", nullable = false)
    private LocalDate archiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "archive_type", nullable = false, length = 10)
    private AuditArchivePath archiveType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private AuditArchiveStatus status;

    @Column(name = "object_key", nullable = false, length = 100)
    private String objectKey;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "failure_reason", length = FAILURE_REASON_MAX_LENGTH)
    private String failureReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static AuditArchiveExecutionJpaEntity pending(
            LocalDate archiveDate, AuditArchivePath archiveType, LocalDateTime now) {
        AuditArchiveExecutionJpaEntity e = new AuditArchiveExecutionJpaEntity();
        e.archiveDate = archiveDate;
        e.archiveType = archiveType;
        e.objectKey = archiveType.keyOf(archiveDate);
        e.status = AuditArchiveStatus.PENDING;
        e.updatedAt = now;
        return e;
    }

    public boolean isSucceeded() {
        return status == AuditArchiveStatus.SUCCESS;
    }

    public void retry(LocalDateTime now) {
        status = AuditArchiveStatus.PENDING;
        updatedAt = now;
    }

    public void succeed(int archivedRowCount, LocalDateTime now) {
        status = AuditArchiveStatus.SUCCESS;
        rowCount = archivedRowCount;
        failureReason = null;
        completedAt = now;
        updatedAt = now;
    }

    // 사유는 컬럼 길이(500자)에 맞춰 자른다. 저장소 예외 메시지는 길이를 보장하지 않는다
    public void fail(String reason, LocalDateTime now) {
        status = AuditArchiveStatus.FAILURE;
        rowCount = null;
        completedAt = null;
        failureReason =
                reason.length() > FAILURE_REASON_MAX_LENGTH ? reason.substring(0, FAILURE_REASON_MAX_LENGTH) : reason;
        updatedAt = now;
    }
}
