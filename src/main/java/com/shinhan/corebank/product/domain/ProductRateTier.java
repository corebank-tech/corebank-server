package com.shinhan.corebank.product.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ProductRateTier {
    private final ProductRateTierId id;
    private final BigDecimal rate;

    @Builder
    public ProductRateTier(ProductRateTierId id, BigDecimal rate) {
        if (id == null || rate == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.id = id;
        this.rate = rate;
    }
}
