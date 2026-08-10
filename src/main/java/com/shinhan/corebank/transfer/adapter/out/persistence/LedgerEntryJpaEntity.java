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
     * 따라서 저장 전 {@link LedgerEntryIdGenerator#nextId()}로 값을 채워야 한다.
     * 이 값은 전용 채번 테이블(ledger_entry_id_sequence)의 AUTO_INCREMENT로 생성되므로
     * 항상 이 방법으로만 채번한다면 테이블 전체에서 전역 유일하다. 다만 파티션 테이블 제약상
     * DB가 이를 강제하지는 않는다({@link LedgerEntryJpaRepository#findByLedgerEntryId} 참고).
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
     * ledgerEntryId는 {@link LedgerEntryIdGenerator}로 채번되어 테이블 전체에서 전역
     * 유일하므로, occurredAt 없이 이 값 하나로 원거래 원장 행을 특정할 수 있다
     * (조회는 {@link LedgerEntryJpaRepository#findByLedgerEntryId} 사용).
     */
    @Column(name = "reversal_id")
    private Long reversalId;
}
