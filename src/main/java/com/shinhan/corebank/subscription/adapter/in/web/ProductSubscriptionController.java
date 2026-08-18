package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionQueryUseCase;
import com.shinhan.corebank.subscription.domain.ProductSubscriptionResult;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/product-subscriptions")
@RequiredArgsConstructor
public class ProductSubscriptionController {
    private final ProductSubscriptionQueryUseCase productSubscriptionQueryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @GetMapping("/{subscriptionId}")
    public ApiResponse<ProductSubscriptionResultResponse> getProductSubscriptions(
            @PathVariable @Positive Long subscriptionId) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        ProductSubscriptionResult result = productSubscriptionQueryUseCase.getResult(subscriptionId, customerId);
        return ApiResponse.success(ProductSubscriptionResultResponse.from(result));
    }
}
