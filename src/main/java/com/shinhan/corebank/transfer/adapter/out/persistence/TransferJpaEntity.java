package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.transfer.domain.TransferChannel;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import com.shinhan.corebank.transfer.domain.TransferType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "transfer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class TransferJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id")
    private Long transferId;

    @Column(name = "transaction_number", length = 20, unique = true, nullable = false)
    private String transactionNumber;

    @Column(name = "withdrawal_account_id", nullable = false)
    private Long withdrawalAccountId;

    @Column(name = "deposit_account_id", nullable = false)
    private Long depositAccountId;

    @Column(name = "deposit_account_number", length = 12, nullable = false)
    private String depositAccountNumber;

    @Column(name = "payee_name", length = 50, nullable = false)
    private String payeeName;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "fee", nullable = false)
    private long fee;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", length = 12, nullable = false)
    private TransferType transferType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 2, nullable = false, columnDefinition = "char(2)")
    private TransferChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 12, nullable = false)
    private ProcessResultStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 12)
    private TransferSourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "execution_date")
    private LocalDate executionDate;

    @Column(name = "my_passbook_memo", length = 10)
    private String myPassbookMemo;

    @Column(name = "recipient_passbook_memo", length = 10)
    private String recipientPassbookMemo;

    @Column(name = "withdrawal_balance_after")
    private Long withdrawalBalanceAfter;

    @Column(name = "error_code", length = 10)
    private String errorCode;

    @Column(name = "error_message", length = 200)
    private String errorMessage;

    @Column(name = "transferred_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime transferredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
}
