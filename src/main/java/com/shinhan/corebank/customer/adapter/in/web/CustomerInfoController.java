package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.customer.application.port.in.CustomerInfoQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.CustomerInfoResult;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoResult;
import com.shinhan.corebank.customer.application.port.in.UpdateCustomerInfoUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

// 로그인 고객의 기본정보 조회 API를 제공한다.
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerInfoController {

    private static final String UPDATE_ENDPOINT = "PATCH /customers/me";
    private static final String UPDATE_SUCCESS_MESSAGE =
            "고객정보가 변경되었습니다.";

    private final CustomerInfoQueryUseCase customerInfoQueryUseCase;
    private final UpdateCustomerInfoUseCase updateCustomerInfoUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @GetMapping("/me")
    public ApiResponse<CustomerInfoResponse> getCustomerInfo() {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        CustomerInfoResult result =
                customerInfoQueryUseCase.getCustomerInfo(customerId);

        return ApiResponse.success(CustomerInfoResponse.from(result));
    }

    // 로그인 고객의 연락처를 멱등하게 변경한다.
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<CustomerInfoUpdateResponse>>
            updateCustomerInfo(
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
                    @RequestBody CustomerInfoUpdateRequest request
            ) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                UPDATE_ENDPOINT,
                new CustomerInfoUpdateFingerprint(
                        customerId,
                        request.phoneNumber(),
                        request.email(),
                        request.emailVerificationToken()
                ),
                new TypeReference<>() {
                },
                () -> {
                    UpdateCustomerInfoResult result =
                            updateCustomerInfoUseCase.update(
                                    request.toCommand(customerId)
                            );
                    return ApiResponse.success(
                            CustomerInfoUpdateResponse.from(result),
                            UPDATE_SUCCESS_MESSAGE
                    );
                }
        );
    }

    // 고객·연락처·이메일 인증 토큰을 포함한 고객정보 변경 요청 지문이다.
    private record CustomerInfoUpdateFingerprint(
            Long customerId,
            String phoneNumber,
            String email,
            String emailVerificationToken
    ) {
    }
}
