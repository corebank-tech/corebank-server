package com.shinhan.corebank.paymentstop.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "case_disputed_transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CaseDisputedTransferJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "disputed_transfer_id")
    private Long disputedTransferId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Column(name = "transaction_number", nullable = false, columnDefinition = "CHAR(20)")
    private String transactionNumber;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime occurredAt;

    static CaseDisputedTransferJpaEntity of(long caseId, String transactionNumber, long amount, LocalDateTime at) {
        CaseDisputedTransferJpaEntity entity = new CaseDisputedTransferJpaEntity();
        entity.caseId = caseId;
        entity.transactionNumber = transactionNumber;
        entity.amount = amount;
        entity.occurredAt = at;
        return entity;
    }
}
