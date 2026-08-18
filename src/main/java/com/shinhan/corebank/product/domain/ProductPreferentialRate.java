package com.shinhan.corebank.product.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder
public class ProductPreferentialRate {
    private ProductPreferentialRateId productPreferentialRateId;
    private String conditionName;
    private BigDecimal rate;
}
