package com.shinhan.corebank.product.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ProductTerms {
    private final ProductTermsId id;
    private final Short displayOrder;

    @Builder
    public ProductTerms(ProductTermsId id, Short displayOrder) {
        if (id == null || displayOrder == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.id = id;
        this.displayOrder = displayOrder;
    }
}
