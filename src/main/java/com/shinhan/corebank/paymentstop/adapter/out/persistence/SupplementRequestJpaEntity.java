package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.SupplementRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "objection_supplement_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplementRequestJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplement_request_id")
    private Long supplementRequestId;

    @Column(name = "objection_id", nullable = false)
    private Long objectionId;

    @Column(name = "round", nullable = false, columnDefinition = "TINYINT")
    private Byte round;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_documents", nullable = false)
    private List<String> missingDocuments;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime requestedAt;

    @Column(name = "submitted_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime submittedAt;

    @Column(name = "submit_note", length = 1000)
    private String submitNote;

    static SupplementRequestJpaEntity of(SupplementRequest request) {
        SupplementRequestJpaEntity entity = new SupplementRequestJpaEntity();
        entity.objectionId = request.objectionId();
        entity.round = (byte) request.round();
        entity.reason = request.reason();
        entity.missingDocuments = request.missingDocuments();
        entity.dueDate = request.dueDate();
        entity.requestedBy = request.requestedBy();
        entity.requestedAt = request.requestedAt();
        return entity;
    }

    void submit(LocalDateTime at, String note) {
        this.submittedAt = at;
        this.submitNote = note;
    }

    SupplementRequest toDomain() {
        return new SupplementRequest(
                objectionId,
                round,
                reason,
                missingDocuments,
                dueDate,
                requestedBy,
                requestedAt,
                submittedAt,
                submitNote);
    }
}
