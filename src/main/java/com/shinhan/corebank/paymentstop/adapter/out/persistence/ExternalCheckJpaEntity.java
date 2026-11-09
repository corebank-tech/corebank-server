package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.paymentstop.domain.external.ExternalAgency;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheck;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheckReply;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheckStatus;
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
@Table(name = "objection_external_check")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalCheckJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "external_check_id")
    private Long externalCheckId;

    @Column(name = "objection_id", nullable = false)
    private Long objectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agency", nullable = false, length = 10)
    private ExternalAgency agency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ExternalCheckStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "reply", length = 24)
    private ExternalCheckReply reply;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "requested_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime requestedAt;

    @Column(name = "replied_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime repliedAt;

    static ExternalCheckJpaEntity of(ExternalCheck check) {
        ExternalCheckJpaEntity entity = new ExternalCheckJpaEntity();
        entity.objectionId = check.objectionId();
        entity.agency = check.agency();
        entity.status = check.status();
        entity.reply = check.reply();
        entity.requestedBy = check.requestedBy();
        entity.requestedAt = check.requestedAt();
        entity.repliedAt = check.repliedAt();
        return entity;
    }

    ExternalCheck toDomain() {
        return new ExternalCheck(
                externalCheckId, objectionId, agency, status, reply, requestedBy, requestedAt, repliedAt);
    }
}
