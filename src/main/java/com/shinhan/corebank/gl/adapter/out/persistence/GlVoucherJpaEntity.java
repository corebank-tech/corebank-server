package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.gl.domain.GlTxType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 전표 — 분개를 담는 단위 (PH-20).
 *
 * <p>{@link com.shinhan.corebank.common.entity.BaseEntity} 를 상속하지 않는다. 그쪽은
 * {@code @LastModifiedDate updatedAt} 까지 매핑하는데 {@code gl_voucher} 에는 그 컬럼이 없다.
 * 전표는 수정하지 않기 때문이다 — 틀리면 지우거나 고치지 않고 정정 전표를 새로 세운다.
 *
 * <p>전표번호 채번(영업일 + 유형 + 일련)과 전표 단위 차대변 일치 검증은 PH-21 이다.
 */
@Entity
@Table(name = "gl_voucher")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class GlVoucherJpaEntity {

    @Id
    @Column(name = "voucher_no", length = 20)
    private String voucherNo;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 24)
    private GlTxType txType;

    @Column(name = "description", length = 200)
    private String description;

    /**
     * DDL 이 {@code NOT NULL DEFAULT CURRENT_TIMESTAMP(6)} 이지만 그 기본값에 기대지 않는다.
     * Hibernate 의 기본 INSERT 는 매핑된 컬럼을 전부 싣기 때문에, 값이 없으면 DB 기본값이
     * 아니라 {@code NULL} 이 들어가 NOT NULL 위반이 된다. `created_at` 만 있는 테이블에
     * {@code BaseEntity} 대신 쓰는 방식은 {@code TransferJpaEntity} 의 선례를 따랐다.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
}
