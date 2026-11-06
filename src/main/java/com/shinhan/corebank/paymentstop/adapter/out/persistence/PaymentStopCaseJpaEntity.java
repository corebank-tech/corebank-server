package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import com.shinhan.corebank.paymentstop.domain.CaseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_stop_case")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentStopCaseJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "case_id")
    private Long caseId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "sender_account_id")
    private Long senderAccountId;

    @Column(name = "objection_deadline", nullable = false)
    private LocalDate objectionDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private CaseStatus status;

    @Column(name = "registered_by", nullable = false)
    private Long registeredBy;

    static PaymentStopCaseJpaEntity of(
            long accountId, Long senderAccountId, LocalDate objectionDeadline, CaseStatus status, long registeredBy) {
        PaymentStopCaseJpaEntity entity = new PaymentStopCaseJpaEntity();
        entity.accountId = accountId;
        entity.senderAccountId = senderAccountId;
        entity.objectionDeadline = objectionDeadline;
        entity.status = status;
        entity.registeredBy = registeredBy;
        return entity;
    }

    void changeStatus(CaseStatus status) {
        this.status = status;
    }
}
