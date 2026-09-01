package com.shinhan.corebank.subscription.application;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.product.application.port.in.ProductQueryUseCase;
import com.shinhan.corebank.product.domain.ProductDetail;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionQueryUseCase;
import com.shinhan.corebank.subscription.application.port.out.AccountNumberQueryPort;
import com.shinhan.corebank.subscription.application.port.out.ProductSubscriptionQueryPort;
import com.shinhan.corebank.subscription.domain.ProductSubscription;
import com.shinhan.corebank.subscription.domain.ProductSubscriptionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductSubscriptionQueryService implements ProductSubscriptionQueryUseCase {
    private final ProductSubscriptionQueryPort productSubscriptionQueryPort;
    private final ProductQueryUseCase productQueryUseCase;
    private final AccountNumberQueryPort accountNumberQueryPort;

    @Override
    public ProductSubscriptionResult getResult(Long subscriptionId, Long requestingCustomerId) {
        ProductSubscription subscription = productSubscriptionQueryPort
                .findByIdAndCustomerId(subscriptionId, requestingCustomerId)
                .orElseThrow(() -> new BusinessException(SubscriptionErrorCode.SUBSCRIPTION_NOT_FOUND));

        ProductDetail productDetail = productQueryUseCase.getDetail(subscription.getProductId());

        String accountNumber = null;

        if (subscription.getAccountId() != null) {
            accountNumber = accountNumberQueryPort
                    .findAccountNumber(subscription.getAccountId(), requestingCustomerId)
                    .orElseThrow(() -> new BusinessException(SubscriptionErrorCode.SUBSCRIPTION_ACCOUNT_NOT_FOUND));
        }

        return ProductSubscriptionResult.builder()
                .subscription(subscription)
                .productName(productDetail.getProduct().getProductName())
                .productGroup(productDetail.getProduct().getProductGroup())
                .accountNumber(accountNumber)
                .build();
    }
}
