package com.shinhan.corebank.subscription.domain;

import com.shinhan.corebank.product.domain.ProductGroup;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductSubscriptionResult {
    private final ProductSubscription subscription;
    private final String productName;
    private final ProductGroup productGroup;
    private final String accountNumber; // 마스킹 전 원본, nullable(가입 실패 건)
}
