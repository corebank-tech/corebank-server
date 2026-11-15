package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import com.shinhan.corebank.paymentstop.domain.Objection;
import com.shinhan.corebank.paymentstop.domain.ObjectionChannel;
import com.shinhan.corebank.paymentstop.domain.ObjectionStatus;
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
    @Column(name = "status", nullable = false, length = 20)
    private ObjectionStatus status;

    @Column(name = "received_by", nullable = false)
    private Long receivedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "received_channel", nullable = false, length = 10)
    private ObjectionChannel receivedChannel;

    @Column(name = "received_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime receivedAt;

    @Column(name = "completeness_confirmed_by")
    private Long completenessConfirmedBy;

    @Column(name = "completeness_confirmed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime completenessConfirmedAt;

    @Column(name = "review_deadline")
    private LocalDate reviewDeadline;

    @Column(name = "supplement_round", nullable = false, columnDefinition = "TINYINT")
    private Byte supplementRound;

    @Column(name = "supplement_due")
    private LocalDate supplementDue;

    static ObjectionJpaEntity of(
            long caseId,
            int clause,
            String statement,
            boolean simplifiedRequested,
            boolean idCopyConfirmed,
            boolean litigationPending,
            ObjectionStatus status,
            long receivedBy,
            ObjectionChannel receivedChannel,
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
        entity.receivedChannel = receivedChannel;
        entity.receivedAt = receivedAt;
        entity.supplementRound = 0;
        return entity;
    }

    void apply(ObjectionStatus status, Objection.Progress progress) {
        this.status = status;
        this.completenessConfirmedBy = progress.completenessConfirmedBy();
        this.completenessConfirmedAt = progress.completenessConfirmedAt();
        this.reviewDeadline = progress.reviewDeadline();
        this.supplementRound = (byte) progress.supplementRound();
        this.supplementDue = progress.supplementDue();
    }

    Objection.Progress progress() {
        return new Objection.Progress(
                completenessConfirmedBy, completenessConfirmedAt, reviewDeadline, supplementRound, supplementDue);
    }
}
