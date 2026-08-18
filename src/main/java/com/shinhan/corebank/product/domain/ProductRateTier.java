package com.shinhan.corebank.product.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
@Builder
public class ProductRateTier {
    private ProductRateTierId id;
    private BigDecimal rate;
}
