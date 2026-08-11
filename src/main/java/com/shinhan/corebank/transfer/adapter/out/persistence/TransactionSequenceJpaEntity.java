package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "transaction_sequence")
@IdClass(TransactionSequenceId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TransactionSequenceJpaEntity {

    /** ck_txseq_range CHECK 제약과 동일한 상한. 거래번호 일련번호부(10자리)를 초과하지 않도록 한다. */
    private static final long MAX_SEQUENCE = 9_999_999_999L;

    @Id
    @Column(name = "seq_date", nullable = false)
    private LocalDate seqDate;

    @Id
    @Column(name = "channel", length = 2, nullable = false, columnDefinition = "char(2)")
    private String channel;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    public long incrementAndGet() {
        if (this.lastSeq >= MAX_SEQUENCE) {
            throw new IllegalStateException(
                    "Transaction sequence exhausted for " + seqDate + "/" + channel + ": " + this.lastSeq);
        }

        this.lastSeq += 1;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
        return this.lastSeq;
    }
}
