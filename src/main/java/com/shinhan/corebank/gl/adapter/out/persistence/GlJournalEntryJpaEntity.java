package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.gl.domain.JournalDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 분개 — 전표 안의 한 줄 (PH-20).
 *
 * <p>계정과목 하나와 한쪽 금액을 가진다. 금액은 항상 양수이고 차변인지 대변인지는
 * {@code drCr} 이 말한다 (Apache Fineract {@code acc_gl_journal_entry.type_enum} + {@code amount}
 * 와 같은 형태). 금액을 {@code Long} 원 단위 정수로 두는 것은 Fineract 의 {@code DECIMAL(19,6)}
 * 과 다른 지점인데, 원화는 소수 자리가 없는 통화라 이 레포는 금액을 정수로 고정한다.
 *
 * <p>{@code tradeDate} 는 전표에서 복제한 값이다. 600만 줄을 기간으로 거를 때 전표 조인을
 * 없앤다 — Fineract 도 {@code entry_date} 를 분개 줄에 둔다.
 *
 * <p>{@code voucherNo}·{@code accountCode} 는 FK 지만 연관관계 매핑(@ManyToOne)을 걸지 않고
 * 값으로 들고 있다. 이 PR 범위는 스키마 골격이고, 조회 경로는 PH-28 시산표 집계(네이티브 SQL)가
 * 정한다 — 쓰지 않을 연관을 미리 만들지 않는다.
 */
@Entity
@Table(name = "gl_journal_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class GlJournalEntryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "voucher_no", nullable = false, length = 20)
    private String voucherNo;

    @Column(name = "line_no", nullable = false)
    private Short lineNo;

    @Column(name = "account_code", nullable = false, columnDefinition = "CHAR(5)")
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "dr_cr", nullable = false, length = 6)
    private JournalDirection drCr;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    /** 사유는 {@link GlVoucherJpaEntity#getCreatedAt()} 쪽 주석과 같다. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
}
