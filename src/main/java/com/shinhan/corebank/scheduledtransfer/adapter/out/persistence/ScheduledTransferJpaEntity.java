package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(name = "scheduled_transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ScheduledTransferJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scheduled_transfer_id")
    private Long scheduledTransferId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "withdrawal_account_id", nullable = false)
    private Long withdrawalAccountId;

    @Column(name = "payee_bank_code", nullable = false, columnDefinition = "CHAR(3)")
    private String payeeBankCode;

    @Column(name = "payee_account_number", nullable = false, columnDefinition = "CHAR(12)")
    private String payeeAccountNumber;

    @Column(name = "payee_name", nullable = false, length = 50)
    private String payeeName;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "my_passbook_memo", length = 10)
    private String myPassbookMemo;

    @Column(name = "recipient_passbook_memo", length = 10)
    private String recipientPassbookMemo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ScheduledTransferStatus status;

    @Column(name = "transaction_number", columnDefinition = "CHAR(20)")
    private String transactionNumber;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
