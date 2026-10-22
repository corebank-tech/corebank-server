package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.subscription.api.InterestPaymentReason;
import com.shinhan.corebank.subscription.application.port.out.InterestPaymentHistoryPort.InterestPaymentRecord;
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
@Table(name = "interest_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterestPaymentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interest_payment_id")
    private Long interestPaymentId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_reason", nullable = false, length = 12)
    private InterestPaymentReason paymentReason;

    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    @Column(name = "tax_amount", nullable = false)
    private Long taxAmount;

    @Column(name = "net_amount", nullable = false)
    private Long netAmount;

    @Column(name = "transaction_number", length = 20)
    private String transactionNumber;

    @Column(name = "trade_date")
    private LocalDate tradeDate;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    public static InterestPaymentJpaEntity from(InterestPaymentRecord payment) {

        InterestPaymentJpaEntity entity = new InterestPaymentJpaEntity();

        entity.accountId = payment.accountId();
        entity.paymentReason = payment.reason();
        entity.grossAmount = payment.gross();
        entity.taxAmount = payment.tax();
        entity.netAmount = payment.net();
        entity.transactionNumber = payment.transactionNumber();
        entity.tradeDate = payment.tradeDate();
        entity.paidAt = payment.paidAt();

        return entity;
    }
}
