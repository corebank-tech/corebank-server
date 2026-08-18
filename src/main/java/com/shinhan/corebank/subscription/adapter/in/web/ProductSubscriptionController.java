package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationUseCase;
import com.shinhan.corebank.subscription.domain.SubscriptionValidation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product-subscriptions")
@RequiredArgsConstructor
public class ProductSubscriptionController {

    private final ProductSubscriptionValidationUseCase productSubscriptionValidationUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @PostMapping("/validation")
    public ApiResponse<ProductSubscriptionValidationResponse> validate(
            @RequestBody @Valid ProductSubscriptionValidationRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        SubscriptionValidation result =
                productSubscriptionValidationUseCase.validate(request.toCommand(customerId));
        return ApiResponse.success(ProductSubscriptionValidationResponse.from(result));
    }
}
