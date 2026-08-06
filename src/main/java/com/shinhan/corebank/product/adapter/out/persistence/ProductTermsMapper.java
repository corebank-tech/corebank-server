package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.ProductTerms;
import com.shinhan.corebank.product.domain.ProductTermsId;

final class ProductTermsMapper {

    private ProductTermsMapper() {
    }

    static ProductTerms toDomain(ProductTermsJpaEntity entity) {
        return ProductTerms.builder()
                .id(toDomain(entity.getId()))
                .displayOrder(entity.getDisplayOrder())
                .build();
    }

    static ProductTermsJpaEntity toEntity(ProductTerms domain) {
        return ProductTermsJpaEntity.builder()
                .id(toEntity(domain.getId()))
                .displayOrder(domain.getDisplayOrder())
                .build();
    }

    static ProductTermsId toDomain(ProductTermsJpaEntityId id) {
        return new ProductTermsId(id.getProductId(), id.getTermsId());
    }

    static ProductTermsJpaEntityId toEntity(ProductTermsId id) {
        return new ProductTermsJpaEntityId(id.getProductId(), id.getTermsId());
    }
}
