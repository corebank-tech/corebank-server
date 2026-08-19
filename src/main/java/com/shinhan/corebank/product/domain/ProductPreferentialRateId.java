package com.shinhan.corebank.product.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ProductPreferentialRateId {
    private final Long productId;
    private final String conditionCode;

    public ProductPreferentialRateId(Long productId, String conditionCode) {
        if (productId == null || conditionCode == null || conditionCode.isBlank()) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.productId = productId;
        this.conditionCode = conditionCode;
    }
}
