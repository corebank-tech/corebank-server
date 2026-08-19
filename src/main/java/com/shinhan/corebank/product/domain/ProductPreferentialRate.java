package com.shinhan.corebank.product.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ProductPreferentialRate {
    private final ProductPreferentialRateId productPreferentialRateId;
    private final String conditionName;
    private final BigDecimal rate;

    @Builder
    public ProductPreferentialRate(ProductPreferentialRateId productPreferentialRateId,
                                   String conditionName,
                                   BigDecimal rate) {
        if (productPreferentialRateId == null
                || conditionName == null || conditionName.isBlank()
                || rate == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        this.productPreferentialRateId = productPreferentialRateId;
        this.conditionName = conditionName;
        this.rate = rate;
    }
}
