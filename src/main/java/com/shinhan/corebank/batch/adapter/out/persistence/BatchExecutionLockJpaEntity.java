package com.shinhan.corebank.batch.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch_execution_lock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchExecutionLockJpaEntity {

    @Id
    @Column(name = "job_name", length = 50)
    private String jobName;

    @Column(name = "currently_running", nullable = false)
    private boolean currentlyRunning;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void markRunning(LocalDateTime now) {
        this.currentlyRunning = true;
        this.updatedAt = now;
    }

    public void markIdle(LocalDateTime now) {
        this.currentlyRunning = false;
        this.updatedAt = now;
    }

    // 마지막 갱신 이후 staleThreshold가 지났으면 서버 크래시 등으로 release()가 못 불린 것으로 본다
    public boolean isStale(LocalDateTime now, Duration staleThreshold) {
        return updatedAt.isBefore(now.minus(staleThreshold));
    }
}
