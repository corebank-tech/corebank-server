package com.shinhan.corebank.product.adapter.in.web;

import com.shinhan.corebank.product.domain.Product;
import com.shinhan.corebank.product.domain.ProductGroup;

import java.math.BigDecimal;

public record ProductListItemResponse(
        Long productId,
        String productCode,
        String productName,
        ProductGroup productGroup,
        String summary,
        BigDecimal baseRate,
        BigDecimal maxRate,
        int minTermMonths,
        int maxTermMonths,
        Long minAmount,
        Long maxAmount,
        Boolean newProduct
) {
    public static ProductListItemResponse from(Product product) {
        return new ProductListItemResponse(
                product.getProductId(),
                product.getProductCode(),
                product.getProductName(),
                product.getProductGroup(),
                product.getSummary(),
                product.getBaseRate(),
                product.getMaxRate(),
                product.getMinTermMonths(),
                product.getMaxTermMonths(),
                product.getMinAmount(),
                product.getMaxAmount(),
                product.getNewFlag()
        );
    }
}
