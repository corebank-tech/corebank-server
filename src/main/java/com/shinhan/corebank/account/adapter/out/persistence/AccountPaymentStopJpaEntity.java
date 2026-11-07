package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.domain.AccountPaymentStop;
import com.shinhan.corebank.account.domain.AccountStatus;
import com.shinhan.corebank.account.domain.PaymentStopType;
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
@Table(name = "account_payment_stop")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountPaymentStopJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_stop_id")
    private Long paymentStopId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 10)
    private PaymentStopType stopType;

    @Column(name = "requested_amount", nullable = false)
    private Long requestedAmount;

    @Column(name = "applied_amount", nullable = false)
    private Long appliedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before", nullable = false, length = 12)
    private AccountStatus statusBefore;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "released_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime releasedAt;

    static AccountPaymentStopJpaEntity from(AccountPaymentStop domain) {
        AccountPaymentStopJpaEntity entity = new AccountPaymentStopJpaEntity();

        entity.paymentStopId = domain.getPaymentStopId();
        entity.accountId = domain.getAccountId();
        entity.caseId = domain.getCaseId();
        entity.statusBefore = domain.getStatusBefore();
        entity.createdAt = domain.getCreatedAt();
        entity.updateFrom(domain);

        return entity;
    }

    void updateFrom(AccountPaymentStop domain) {
        this.stopType = domain.getStopType();
        this.requestedAmount = domain.getRequestedAmount();
        this.appliedAmount = domain.getAppliedAmount();
        this.active = domain.isActive();
        this.releasedAt = domain.getReleasedAt();
    }

    AccountPaymentStop toDomain() {
        return AccountPaymentStop.reconstitute(
                paymentStopId,
                accountId,
                caseId,
                stopType,
                requestedAmount,
                appliedAmount,
                statusBefore,
                active,
                createdAt,
                releasedAt);
    }
}
