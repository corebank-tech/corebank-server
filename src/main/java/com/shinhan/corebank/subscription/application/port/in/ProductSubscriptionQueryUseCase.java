package com.shinhan.corebank.subscription.application.port.in;

import com.shinhan.corebank.subscription.domain.ProductSubscriptionResult;

public interface ProductSubscriptionQueryUseCase {
    ProductSubscriptionResult getResult(Long subscriptionId, Long requestingCustomerId);
}
