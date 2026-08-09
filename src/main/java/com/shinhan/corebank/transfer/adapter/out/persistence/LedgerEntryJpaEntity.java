package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDateTime;

import com.shinhan.corebank.transfer.domain.LedgerDirection;
import com.shinhan.corebank.transfer.domain.TransferChannel;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ledger_entry")
@IdClass(LedgerEntryId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LedgerEntryJpaEntity {

    @Id
    @Column(name = "ledger_entry_id")
    private Long ledgerEntryId;

    @Id
    @Column(name = "occurred_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime occurredAt;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "transfer_id")
    private Long transferId;

    @Column(name = "transaction_number", length = 20, nullable = false)
    private String transactionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 10, nullable = false)
    private LedgerDirection direction;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "transaction_type", length = 32, nullable = false)
    private String transactionType;

    @Column(name = "transaction_content", length = 10)
    private String transactionContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 2, nullable = false, columnDefinition = "char(2)")
    private TransferChannel channel;

    @Column(name = "reversed", nullable = false)
    private boolean reversed;

    @Column(name = "reversal_id")
    private Long reversalId;
}
