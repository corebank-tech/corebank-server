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
@Builder(toBuilder = true)
public class LedgerEntryJpaEntity {

    /**
     * DB 컬럼은 AUTO_INCREMENT이지만, Hibernate는 {@code @IdClass} 복합키의 구성 필드에
     * IDENTITY 채번 전략을 지원하지 않는다({@code IdentifierGenerationException: Identity
     * generation isn't supported for composite ids} — 통합 테스트로 실측 확인).
     * 따라서 이 필드는 여전히 애플리케이션이 저장 전에 값을 채워야 하며(별도 시퀀스/채번 서비스 필요),
     * 채번 전략 자체는 이 PR 범위를 벗어나는 별도 설계 결정 사항이다.
     */
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

    /**
     * 반대기표(REVERSAL)가 가리키는 원거래의 ledger_entry_id.
     * DB의 ledger_entry_id는 AUTO_INCREMENT라 전역 유일이 보장되므로, 채번 전략이 확정되면
     * occurredAt 없이 이 값 하나로 원거래 원장 행을 특정할 수 있다(조회는
     * {@link LedgerEntryJpaRepository#findByLedgerEntryId} 사용). 다만 위 ledgerEntryId
     * 채번 전략이 아직 정해지지 않았으므로 이 전제도 함께 확정되어야 한다.
     */
    @Column(name = "reversal_id")
    private Long reversalId;
}
