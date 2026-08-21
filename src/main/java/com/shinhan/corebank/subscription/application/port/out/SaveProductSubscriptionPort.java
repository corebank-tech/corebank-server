package com.shinhan.corebank.subscription.application.port.out;

import com.shinhan.corebank.subscription.domain.ProductSubscription;

public interface SaveProductSubscriptionPort {
    ProductSubscription save(ProductSubscription subscription);
}
