package com.shinhan.corebank.transfer.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /** 다음 번호부터 최대 size개를 예약하고 예약한 끝 번호를 돌려준다. maxSeq를 넘는 만큼은 잘라낸다. */
    public long reserve(int size, long maxSeq, LocalDateTime now) {
        this.lastSeq = Math.min(this.lastSeq + size, maxSeq);
        this.updatedAt = now;
        return this.lastSeq;
    }
}
