package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.subscription.application.port.out.ExistingSubscriptionPort;
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionQueryPort;
import com.shinhan.corebank.subscription.application.port.out.SaveProductSubscriptionPort;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductSubscriptionPersistenceAdapter
        implements ProductSubscriptionQueryPort, SaveProductSubscriptionPort, ExistingSubscriptionPort {
    private final ProductSubscriptionJpaRepository productSubscriptionJpaRepository;

    @Override
    public Optional<ProductSubscription> findByIdAndCustomerId(Long subscriptionId, Long customerId) {
        return productSubscriptionJpaRepository.findBySubscriptionIdAndCustomerId(subscriptionId, customerId)
                .map(ProductSubscriptionMapper::toDomain);
    }

    @Override
    public ProductSubscription save(ProductSubscription subscription) {
        ProductSubscriptionJpaEntity saved =
                productSubscriptionJpaRepository.save(ProductSubscriptionMapper.toEntity(subscription));
        return ProductSubscriptionMapper.toDomain(saved);
    }

    @Override
    public boolean existsActiveSubscription(Long customerId, Long productId) {
        return productSubscriptionJpaRepository.existsByCustomerIdAndProductIdAndStatus(
                customerId, productId, ProcessResultStatus.SUCCESS);
    }
}
