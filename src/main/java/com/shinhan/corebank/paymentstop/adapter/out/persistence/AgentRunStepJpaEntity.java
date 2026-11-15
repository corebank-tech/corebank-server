package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.agent.AgentStepStatus;
import com.shinhan.corebank.paymentstop.domain.agent.AgentStepType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_run_step")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentRunStepJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "step_id")
    private Long stepId;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AgentStepType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail")
    private Map<String, Object> detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private AgentStepStatus status;

    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime startedAt;

    @Column(name = "ended_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime endedAt;

    static AgentRunStepJpaEntity of(
            long runId,
            int seq,
            AgentStepType type,
            String title,
            String summary,
            Map<String, Object> detail,
            AgentStepStatus status,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {
        AgentRunStepJpaEntity entity = new AgentRunStepJpaEntity();
        entity.runId = runId;
        entity.seq = seq;
        entity.type = type;
        entity.title = title;
        entity.summary = summary;
        entity.detail = detail;
        entity.status = status;
        entity.startedAt = startedAt;
        entity.endedAt = endedAt;
        return entity;
    }
}
