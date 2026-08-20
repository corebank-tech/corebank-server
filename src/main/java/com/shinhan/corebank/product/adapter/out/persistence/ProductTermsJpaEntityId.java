package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 도메인 미러({@link com.shinhan.corebank.product.domain.ProductTermsId})와 달리 이쪽은
 * 애플리케이션 파라미터로부터 직접 생성된다({@code ProductPersistenceAdapter#existsProductTerms}).
 * null 이 실제로 유입될 수 있는 경로라 생성자에서 막는다.
 *
 * {@code @Embeddable} 이라 Hibernate 가 기본 생성자로 인스턴스를 만든 뒤 필드를 채운다.
 * 기본 생성자를 제거하면 안 되고, 같은 이유로 필드를 final 로 둘 수 없다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductTermsJpaEntityId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long productId;
    private Long termsId;

    public ProductTermsJpaEntityId(Long productId, Long termsId) {
        if (productId == null || termsId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.productId = productId;
        this.termsId = termsId;
    }
}
