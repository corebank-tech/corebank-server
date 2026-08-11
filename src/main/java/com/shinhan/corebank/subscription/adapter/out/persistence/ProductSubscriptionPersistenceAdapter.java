package com.shinhan.corebank.subscription.adapter.out.persistence;

import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionQueryPort;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductSubscriptionPersistenceAdapter implements ProductSubscriptionQueryPort {
    private final ProductSubscriptionJpaRepository productSubscriptionJpaRepository;

    @Override
    public Optional<ProductSubscription> findById(Long subscriptionId) {
        return productSubscriptionJpaRepository.findById(subscriptionId)
                .map(ProductSubscriptionMapper::toDomain);
    }
}
