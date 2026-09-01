package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.ProductRateTier;
import com.shinhan.corebank.product.domain.ProductRateTierId;

final class ProductRateTierMapper {

    private ProductRateTierMapper() {}

    static ProductRateTier toDomain(ProductRateTierJpaEntity entity) {
        return ProductRateTier.builder()
                .id(toDomain(entity.getId()))
                .rate(entity.getRate())
                .build();
    }

    static ProductRateTierJpaEntity toEntity(ProductRateTier domain) {
        return ProductRateTierJpaEntity.builder()
                .id(toEntity(domain.getId()))
                .rate(domain.getRate())
                .build();
    }

    static ProductRateTierId toDomain(ProductRateTierJpaEntityId id) {
        return new ProductRateTierId(id.getProductId(), id.getTermMonths());
    }

    static ProductRateTierJpaEntityId toEntity(ProductRateTierId id) {
        return new ProductRateTierJpaEntityId(id.getProductId(), id.getTermMonths());
    }
}
