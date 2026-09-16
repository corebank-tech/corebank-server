package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.ProductPreferentialRate;
import com.shinhan.corebank.product.domain.ProductPreferentialRateId;

final class ProductPreferentialRateMapper {

    private ProductPreferentialRateMapper() {}

    static ProductPreferentialRate toDomain(ProductPreferentialRateJpaEntity entity) {
        return ProductPreferentialRate.builder()
                .productPreferentialRateId(toDomain(entity.getProductPreferentialRateId()))
                .conditionName(entity.getConditionName())
                .rate(entity.getRate())
                .build();
    }

    static ProductPreferentialRateJpaEntity toEntity(ProductPreferentialRate domain) {
        return ProductPreferentialRateJpaEntity.builder()
                .productPreferentialRateId(toEntity(domain.getProductPreferentialRateId()))
                .conditionName(domain.getConditionName())
                .rate(domain.getRate())
                .build();
    }

    static ProductPreferentialRateId toDomain(ProductPreferentialRateJpaEntityId id) {
        return new ProductPreferentialRateId(id.getProductId(), id.getConditionCode());
    }

    static ProductPreferentialRateJpaEntityId toEntity(ProductPreferentialRateId id) {
        return new ProductPreferentialRateJpaEntityId(id.getProductId(), id.getConditionCode());
    }
}
