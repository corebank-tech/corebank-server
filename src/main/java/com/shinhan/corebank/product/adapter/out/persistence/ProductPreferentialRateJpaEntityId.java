package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * {@code @Embeddable} 이라 Hibernate 가 기본 생성자로 인스턴스를 만든 뒤 필드를 채운다.
 * 기본 생성자를 제거하면 안 되고, 같은 이유로 필드를 final 로 둘 수 없다.
 *
 * productId 는 primitive 라 null 이 불가능하므로 conditionCode 만 검증한다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductPreferentialRateJpaEntityId implements Serializable {

    private static final long serialVersionUID = 1L;

    private long productId;

    @Column(length = 30)
    private String conditionCode;

    public ProductPreferentialRateJpaEntityId(long productId, String conditionCode) {
        if (conditionCode == null || conditionCode.isBlank()) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.productId = productId;
        this.conditionCode = conditionCode;
    }
}
