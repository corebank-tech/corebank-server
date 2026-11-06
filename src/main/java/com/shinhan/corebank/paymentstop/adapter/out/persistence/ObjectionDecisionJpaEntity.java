package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import com.shinhan.corebank.paymentstop.domain.DecisionStatus;
import com.shinhan.corebank.paymentstop.domain.ObjectionDecision;
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

// active_objection_id(생성 열)는 매핑하지 않는다. DB 가 계산하고 유니크 키로만 쓴다
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DecisionStatus status;

    @Column(name = "decided_by", nullable = false)
    private Long decidedBy;

    @Column(name = "decided_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime decidedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime approvedAt;

    @Column(name = "returned_by")
    private Long returnedBy;

    @Column(name = "returned_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime returnedAt;

    @Column(name = "return_reason", length = 500)
    private String returnReason;

    static ObjectionDecisionJpaEntity of(ObjectionDecision decision) {
        ObjectionDecisionJpaEntity entity = new ObjectionDecisionJpaEntity();
        entity.objectionId = decision.objectionId();
        entity.result = decision.result();
        entity.reason = decision.reason();
        entity.earlyReleaseBasis = decision.earlyReleaseBasis();
        entity.decidedBy = decision.decidedBy();
        entity.decidedAt = decision.decidedAt();
        entity.apply(decision);
        return entity;
    }

    void apply(ObjectionDecision decision) {
        this.status = decision.status();
        this.requestedHoldAmount = decision.requestedHoldAmount();
        this.appliedHoldAmount = decision.appliedHoldAmount();
        this.approvedBy = decision.approvedBy();
        this.approvedAt = decision.approvedAt();
        this.returnedBy = decision.returnedBy();
        this.returnedAt = decision.returnedAt();
        this.returnReason = decision.returnReason();
    }

    ObjectionDecision toDomain() {
        return new ObjectionDecision(
                decisionId,
                objectionId,
                result,
                status,
                reason,
                earlyReleaseBasis,
                requestedHoldAmount,
                appliedHoldAmount,
                decidedBy,
                decidedAt,
                approvedBy,
                approvedAt,
                returnedBy,
                returnedAt,
                returnReason);
    }
}
