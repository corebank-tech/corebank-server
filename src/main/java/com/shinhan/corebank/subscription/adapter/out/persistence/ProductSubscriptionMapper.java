package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.subscription.domain.ProductSubscription;

final class ProductSubscriptionMapper {
    private ProductSubscriptionMapper() {
    }

    static ProductSubscription toDomain(ProductSubscriptionJpaEntity entity) {
        return ProductSubscription.builder()
                .subscriptionId(entity.getSubscriptionId())
                .customerId(entity.getCustomerId())
                .productId(entity.getProductId())
                .accountId(entity.getAccountId())
                .withdrawalAccountId(entity.getWithdrawalAccountId())
                .subscriptionAmount(entity.getSubscriptionAmount())
                .termMonths(entity.getTermMonths())
                .paymentDay(entity.getPaymentDay())
                .baseRate(entity.getBaseRate())
                .preferentialRate(entity.getPreferentialRate())
                .appliedRate(entity.getAppliedRate())
                .maturityHandling(entity.getMaturityHandling())
                .expectedMaturityAmount(entity.getExpectedMaturityAmount())
                .status(entity.getStatus())
                .transactionNumber(entity.getTransactionNumber())
                .openedDate(entity.getOpenedDate())
                .maturityDate(entity.getMaturityDate())
                .subscribedAt(entity.getSubscribedAt())
                .build();
        }

    static ProductSubscriptionJpaEntity toEntity(ProductSubscription domain) {
        return ProductSubscriptionJpaEntity.builder()
                .subscriptionId(domain.getSubscriptionId())
                .customerId(domain.getCustomerId())
                .productId(domain.getProductId())
                .accountId(domain.getAccountId())
                .withdrawalAccountId(domain.getWithdrawalAccountId())
                .subscriptionAmount(domain.getSubscriptionAmount())
                .termMonths(domain.getTermMonths())
                .paymentDay(domain.getPaymentDay())
                .baseRate(domain.getBaseRate())
                .preferentialRate(domain.getPreferentialRate())
                .appliedRate(domain.getAppliedRate())
                .maturityHandling(domain.getMaturityHandling())
                .expectedMaturityAmount(domain.getExpectedMaturityAmount())
                .status(domain.getStatus())
                .transactionNumber(domain.getTransactionNumber())
                .openedDate(domain.getOpenedDate())
                .maturityDate(domain.getMaturityDate())
                .subscribedAt(domain.getSubscribedAt())
                .build();
    }
}
