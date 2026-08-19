package com.shinhan.corebank.product.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ProductTermsId {
    private final Long productId;
    private final Long termsId;

    public ProductTermsId(Long productId, Long termsId) {
        if (productId == null || termsId == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.productId = productId;
        this.termsId = termsId;
    }
}
