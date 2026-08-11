package com.shinhan.corebank.subscription.application.port.out;

import com.shinhan.corebank.subscription.domain.ProductSubscription;

import java.util.Optional;

public interface ProductSubscriptionQueryPort {
    Optional<ProductSubscription> findById(Long subscriptionId);
}
