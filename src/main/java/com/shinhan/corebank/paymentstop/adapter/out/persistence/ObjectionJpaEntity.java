package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import com.shinhan.corebank.paymentstop.domain.ObjectionStatus;
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
@Table(name = "objection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ObjectionJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "objection_id")
    private Long objectionId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "clause", nullable = false, columnDefinition = "TINYINT")
    private Byte clause;

    @Column(name = "statement", nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(name = "simplified_requested", nullable = false)
    private boolean simplifiedRequested;

    @Column(name = "id_copy_confirmed", nullable = false)
    private boolean idCopyConfirmed;

    @Column(name = "litigation_pending", nullable = false)
    private boolean litigationPending;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ObjectionStatus status;

    @Column(name = "received_by", nullable = false)
    private Long receivedBy;

    @Column(name = "received_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime receivedAt;

    static ObjectionJpaEntity of(
            long caseId,
            int clause,
            String statement,
            boolean simplifiedRequested,
            boolean idCopyConfirmed,
            boolean litigationPending,
            ObjectionStatus status,
            long receivedBy,
            LocalDateTime receivedAt) {
        ObjectionJpaEntity entity = new ObjectionJpaEntity();
        entity.caseId = caseId;
        entity.clause = (byte) clause;
        entity.statement = statement;
        entity.simplifiedRequested = simplifiedRequested;
        entity.idCopyConfirmed = idCopyConfirmed;
        entity.litigationPending = litigationPending;
        entity.status = status;
        entity.receivedBy = receivedBy;
        entity.receivedAt = receivedAt;
        return entity;
    }

    void changeStatus(ObjectionStatus status) {
        this.status = status;
    }
}
