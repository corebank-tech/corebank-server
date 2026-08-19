package com.shinhan.corebank.product.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ProductRateTierId {
    private final Long productId;
    private final Short termMonths;

    public ProductRateTierId(Long productId, Short termMonths) {
        if (productId == null || termMonths == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.productId = productId;
        this.termMonths = termMonths;
    }
}
