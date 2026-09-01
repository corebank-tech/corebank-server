package com.shinhan.corebank.product.domain;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class ProductDetail {
    private Product product;
    private List<ProductRateTier> rateTiers;
    private List<ProductPreferentialRate> preferentialRates;
    private List<ProductTerms> terms;
}
