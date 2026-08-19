package com.shinhan.corebank.product.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class ProductPreferentialRateId {
    private Long productId;
    private String conditionCode;
}
