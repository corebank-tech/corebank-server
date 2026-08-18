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
        this.lastSeq += 1;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
        return this.lastSeq;
    }
}
