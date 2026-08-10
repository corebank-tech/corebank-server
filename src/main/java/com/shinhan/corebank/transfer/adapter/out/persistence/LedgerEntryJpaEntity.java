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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    /**
     * 반대기표(REVERSAL)가 가리키는 원거래의 ledger_entry_id.
     * ledgerEntryId는 AUTO_INCREMENT로 테이블 전체에서 전역 유일하므로
     * occurredAt 없이 이 값 하나로 원거래 원장 행을 특정할 수 있다.
     * 조회는 복합키 findById 대신 {@link LedgerEntryJpaRepository#findByLedgerEntryId}를 사용한다.
     */
    @Column(name = "reversal_id")
    private Long reversalId;
}
