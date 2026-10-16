package com.shinhan.corebank.batch.adapter.out.persistence;

import com.shinhan.corebank.batch.api.BatchStepStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "batch_execution_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchExecutionHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_execution_history_id")
    private Long batchExecutionHistoryId;

    @Column(name = "job_name", nullable = false, length = 50)
    private String jobName;

    @Column(name = "step_name", nullable = false, length = 100)
    private String stepName;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    public static BatchExecutionHistoryJpaEntity of(
            String jobName,
            String stepName,
            LocalDate businessDate,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            BatchStepStatus status,
            String failureReason) {
        BatchExecutionHistoryJpaEntity e = new BatchExecutionHistoryJpaEntity();
        e.jobName = jobName;
        e.stepName = stepName;
        e.businessDate = businessDate;
        e.startedAt = startedAt;
        e.endedAt = endedAt;
        e.status = status.name();
        e.failureReason = failureReason;
        return e;
    }
}
