package com.shinhan.corebank.gl.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import com.shinhan.corebank.gl.domain.GlAccountClass;
import com.shinhan.corebank.gl.domain.GlNormalBalance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정과목 (PH-20).
 *
 * <p>PK 가 채번이 아니라 부여된 코드다 — 대1+중2+세2 = 5자리이고 첫 자리가 분류와 대응한다.
 * 시드는 {@code R__seed_gl_account.sql} 이 넣는다.
 *
 * <p>{@code created_at}·{@code updated_at} 을 모두 가지므로 {@link BaseEntity} 를 상속한다.
 * 전표·분개는 {@code created_at} 만 있어 상속하지 않는다.
 */
@Entity
@Table(name = "gl_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GlAccountJpaEntity extends BaseEntity {

    @Id
    @Column(name = "account_code", columnDefinition = "CHAR(5)")
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 50)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_class", nullable = false, length = 12)
    private GlAccountClass accountClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 6)
    private GlNormalBalance normalBalance;
}
