package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionQueryUseCase;
import com.shinhan.corebank.subscription.domain.ProductSubscriptionResult;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product-subscriptions")
@RequiredArgsConstructor
public class ProductSubscriptionController {
    private final ProductSubscriptionQueryUseCase productSubscriptionQueryUseCase;

    @GetMapping("/{subscriptionId}")
    public ApiResponse<ProductSubscriptionResultResponse> getProductSubscriptions(@PathVariable Long subscriptionId, HttpSession session) {
        // TODO(#45): 세션 인증 필터 완료 후 세션 속성명 확인 필요
        Long customerId = (Long) session.getAttribute("customerId");
        if (customerId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }

        ProductSubscriptionResult result = productSubscriptionQueryUseCase.getResult(subscriptionId, customerId);
        return ApiResponse.success(ProductSubscriptionResultResponse.from(result));
    }
}
