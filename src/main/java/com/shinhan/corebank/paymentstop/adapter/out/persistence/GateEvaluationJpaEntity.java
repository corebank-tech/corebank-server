package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "gate_evaluation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GateEvaluationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gate_evaluation_id")
    private Long gateEvaluationId;

    @Column(name = "objection_id", nullable = false)
    private Long objectionId;

    @Column(name = "purpose", nullable = false, length = 12)
    private String purpose;

    @Column(name = "branch", nullable = false, length = 16)
    private String branch;

    @Column(name = "simplified_excluded", nullable = false)
    private boolean simplifiedExcluded;

    @Column(name = "rules_version", nullable = false, length = 20)
    private String rulesVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings", nullable = false)
    private Map<String, Object> settings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_results", nullable = false)
    private List<Map<String, Object>> ruleResults;

    @Column(name = "evaluated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime evaluatedAt;

    static GateEvaluationJpaEntity of(
            long objectionId,
            String purpose,
            String branch,
            boolean simplifiedExcluded,
            String rulesVersion,
            Map<String, Object> settings,
            List<Map<String, Object>> ruleResults,
            LocalDateTime evaluatedAt) {
        GateEvaluationJpaEntity entity = new GateEvaluationJpaEntity();
        entity.objectionId = objectionId;
        entity.purpose = purpose;
        entity.branch = branch;
        entity.simplifiedExcluded = simplifiedExcluded;
        entity.rulesVersion = rulesVersion;
        entity.settings = settings;
        entity.ruleResults = ruleResults;
        entity.evaluatedAt = evaluatedAt;
        return entity;
    }
}
