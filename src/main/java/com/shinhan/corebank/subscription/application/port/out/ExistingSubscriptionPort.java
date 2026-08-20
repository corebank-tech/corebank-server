package com.shinhan.corebank.subscription.application.port.out;

public interface ExistingSubscriptionPort {
    // 1인 1계좌 제한(PRD0301) 판정용. product.single_account_limit=TRUE인 상품에만 적용된다.
    boolean existsActiveSubscription(Long customerId, Long productId);
}
