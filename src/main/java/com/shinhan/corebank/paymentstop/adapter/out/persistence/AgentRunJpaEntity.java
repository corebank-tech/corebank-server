package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentRunJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Long runId;

    @Column(name = "objection_id", nullable = false)
    private Long objectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private AgentRunStatus status;

    @Column(name = "attempt", nullable = false)
    private Integer attempt;

    @Column(name = "lease_until", columnDefinition = "DATETIME(6)")
    private LocalDateTime leaseUntil;

    @Column(name = "model", length = 60)
    private String model;

    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "cost_micro_usd")
    private Long costMicroUsd;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "queued_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime queuedAt;

    @Column(name = "started_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime startedAt;

    @Column(name = "ended_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime endedAt;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    static AgentRunJpaEntity queued(long objectionId, long requestedBy, LocalDateTime queuedAt) {
        AgentRunJpaEntity entity = new AgentRunJpaEntity();
        entity.objectionId = objectionId;
        entity.status = AgentRunStatus.QUEUED;
        entity.attempt = 0;
        entity.requestedBy = requestedBy;
        entity.queuedAt = queuedAt;
        return entity;
    }

    void extendLease(LocalDateTime leaseUntil) {
        this.leaseUntil = leaseUntil;
    }

    void requeue(String reason) {
        this.status = AgentRunStatus.QUEUED;
        this.leaseUntil = null;
        this.failureReason = reason;
    }

    void fail(LocalDateTime endedAt, String reason) {
        this.status = AgentRunStatus.FAILED;
        this.leaseUntil = null;
        this.endedAt = endedAt;
        this.failureReason = reason;
    }

    void complete(
            LocalDateTime endedAt,
            String model,
            String promptVersion,
            Long inputTokens,
            Long outputTokens,
            Long costMicroUsd) {
        this.status = AgentRunStatus.COMPLETED;
        this.leaseUntil = null;
        this.endedAt = endedAt;
        this.model = model;
        this.promptVersion = promptVersion;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.costMicroUsd = costMicroUsd;
    }
}
