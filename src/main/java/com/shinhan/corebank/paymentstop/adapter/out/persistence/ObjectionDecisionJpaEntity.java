package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.DecisionResult;
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
@Table(name = "objection_decision")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ObjectionDecisionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "decision_id")
    private Long decisionId;

    @Column(name = "objection_id", nullable = false)
    private Long objectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 10)
    private DecisionResult result;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "early_release_basis", length = 500)
    private String earlyReleaseBasis;

    @Column(name = "requested_hold_amount")
    private Long requestedHoldAmount;

    @Column(name = "applied_hold_amount")
    private Long appliedHoldAmount;

    @Column(name = "decided_by", nullable = false)
    private Long decidedBy;

    @Column(name = "decided_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime decidedAt;

    static ObjectionDecisionJpaEntity of(
            long objectionId,
            DecisionResult result,
            String reason,
            String earlyReleaseBasis,
            Long requestedHoldAmount,
            Long appliedHoldAmount,
            long decidedBy,
            LocalDateTime decidedAt) {
        ObjectionDecisionJpaEntity entity = new ObjectionDecisionJpaEntity();
        entity.objectionId = objectionId;
        entity.result = result;
        entity.reason = reason;
        entity.earlyReleaseBasis = earlyReleaseBasis;
        entity.requestedHoldAmount = requestedHoldAmount;
        entity.appliedHoldAmount = appliedHoldAmount;
        entity.decidedBy = decidedBy;
        entity.decidedAt = decidedAt;
        return entity;
    }
}
