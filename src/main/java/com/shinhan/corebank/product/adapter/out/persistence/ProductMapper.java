package com.shinhan.corebank.product.adapter.out.persistence;

import com.shinhan.corebank.product.domain.Product;

final class ProductMapper {

    private ProductMapper() {}

    static Product toDomain(ProductJpaEntity entity) {
        return Product.builder()
                .productId(entity.getProductId())
                .productCode(entity.getProductCode())
                .productName(entity.getProductName())
                .productGroup(entity.getProductGroup())
                .depositType(entity.getDepositType())
                .summary(entity.getSummary())
                .description(entity.getDescription())
                .eligibility(entity.getEligibility())
                .subscriptionRestrictions(entity.getSubscriptionRestrictions())
                .notices(entity.getNotices())
                .baseRate(entity.getBaseRate())
                .maxRate(entity.getMaxRate())
                .minAmount(entity.getMinAmount())
                .maxAmount(entity.getMaxAmount())
                .amountUnit(entity.getAmountUnit())
                .minTermMonths(entity.getMinTermMonths())
                .maxTermMonths(entity.getMaxTermMonths())
                .interestPayType(entity.getInterestPayType())
                .saleStatus(entity.getSaleStatus())
                .saleStartDate(entity.getSaleStartDate())
                .saleEndDate(entity.getSaleEndDate())
                .newFlag(entity.getNewFlag())
                .singleAccountLimit(entity.getSingleAccountLimit())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    static ProductJpaEntity toEntity(Product domain) {
        return ProductJpaEntity.builder()
                .productId(domain.getProductId())
                .productCode(domain.getProductCode())
                .productName(domain.getProductName())
                .productGroup(domain.getProductGroup())
                .depositType(domain.getDepositType())
                .summary(domain.getSummary())
                .description(domain.getDescription())
                .eligibility(domain.getEligibility())
                .subscriptionRestrictions(domain.getSubscriptionRestrictions())
                .notices(domain.getNotices())
                .baseRate(domain.getBaseRate())
                .maxRate(domain.getMaxRate())
                .minAmount(domain.getMinAmount())
                .maxAmount(domain.getMaxAmount())
                .amountUnit(domain.getAmountUnit())
                .minTermMonths(domain.getMinTermMonths())
                .maxTermMonths(domain.getMaxTermMonths())
                .interestPayType(domain.getInterestPayType())
                .saleStatus(domain.getSaleStatus())
                .saleStartDate(domain.getSaleStartDate())
                .saleEndDate(domain.getSaleEndDate())
                .newFlag(domain.getNewFlag())
                .singleAccountLimit(domain.getSingleAccountLimit())
                .build();
    }
}
