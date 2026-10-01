package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.gl.domain.GlTxType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전표번호 일련번호 카운터 (PH-21). (거래일, 유형)마다 한 행이다.
 *
 * <p>구조는 이체 거래번호 카운터({@code TransactionSequenceJpaEntity})와 같다.
 */
@Entity
@Table(name = "gl_voucher_sequence")
@IdClass(GlVoucherSequenceId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GlVoucherSequenceJpaEntity {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 24)
    private GlTxType txType;

    @Column(name = "last_seq", nullable = false)
    private int lastSeq;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    private GlVoucherSequenceJpaEntity(LocalDate tradeDate, GlTxType txType, int lastSeq) {
        this.tradeDate = tradeDate;
        this.txType = txType;
        this.lastSeq = lastSeq;
        this.updatedAt = LocalDateTime.now(KST);
    }

    static GlVoucherSequenceJpaEntity startAt(LocalDate tradeDate, GlTxType txType, int lastSeq) {
        return new GlVoucherSequenceJpaEntity(tradeDate, txType, lastSeq);
    }

    int incrementAndGet() {
        this.lastSeq = this.lastSeq + 1;
        this.updatedAt = LocalDateTime.now(KST);
        return this.lastSeq;
    }
}
