package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase.ProductSubscriptionExecuteResult;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionQueryUseCase;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationUseCase;
import com.shinhan.corebank.subscription.domain.ProductSubscriptionResult;
import com.shinhan.corebank.subscription.domain.SubscriptionValidation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/product-subscriptions")
@RequiredArgsConstructor
public class ProductSubscriptionController {

    private final ProductSubscriptionValidationUseCase productSubscriptionValidationUseCase;
    private final ProductSubscriptionQueryUseCase productSubscriptionQueryUseCase;
    private final ProductSubscriptionExecuteUseCase productSubscriptionExecuteUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping("/validation")
    public ApiResponse<ProductSubscriptionValidationResponse> validate(
            @RequestBody @Valid ProductSubscriptionValidationRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        SubscriptionValidation result =
                productSubscriptionValidationUseCase.validate(request.toCommand(customerId));
        return ApiResponse.success(ProductSubscriptionValidationResponse.from(result));
    }

    @GetMapping("/{subscriptionId}")
    public ApiResponse<ProductSubscriptionResultResponse> getProductSubscriptions(
            @PathVariable @Positive Long subscriptionId) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        ProductSubscriptionResult result = productSubscriptionQueryUseCase.getResult(subscriptionId, customerId);
        return ApiResponse.success(ProductSubscriptionResultResponse.from(result));
    }

    // Idempotency-Key 필수 — api_conventions.md §7-3, "Y" 적용대상에 "상품가입 실행" 명시
    @PostMapping
    public ResponseEntity<ApiResponse<ProductSubscriptionExecuteResponse>> execute(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid ProductSubscriptionExecuteRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return idempotentRequestExecutor.execute(
                idempotencyKey, customerId, "POST /product-subscriptions", fingerprint(request),
                new TypeReference<>() {},
                () -> {
                    ProductSubscriptionExecuteResult result =
                            productSubscriptionExecuteUseCase.execute(request.toCommand(customerId));
                    return ApiResponse.success(
                            ProductSubscriptionExecuteResponse.from(result), "상품 가입이 완료되었습니다.");
                });
    }

    // 멱등키 해시는 *AuthToken으로 끝나는 일회성 인증 토큰만 제외한다 — OTP·계좌비밀번호 인증 토큰을
    // 재발급받아 재시도해도 같은 요청으로 인식되도록(idempotency_key.request_hash 컬럼 코멘트 참고).
    // newAccountPassword/Confirm은 토큰이 아니라 실제 요청 값이라 포함한다.
    private Map<String, Object> fingerprint(ProductSubscriptionExecuteRequest request) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("productId", request.productId());
        fingerprint.put("subscriptionAmount", request.subscriptionAmount());
        fingerprint.put("termMonths", request.termMonths());
        fingerprint.put("withdrawalAccountId", request.withdrawalAccountId());
        fingerprint.put("newAccountPassword", request.newAccountPassword());
        fingerprint.put("newAccountPasswordConfirm", request.newAccountPasswordConfirm());
        fingerprint.put("agreedTerms", request.agreedTerms());
        return fingerprint;
    }
}
