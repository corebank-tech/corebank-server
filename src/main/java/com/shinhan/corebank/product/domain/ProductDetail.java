package com.shinhan.corebank.product.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Builder
public class ProductDetail {
    private Product product;
    private List<ProductRateTier> rateTiers;
    private List<ProductPreferentialRate> preferentialRates;
    private List<ProductTerms> terms;
}
