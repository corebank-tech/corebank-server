package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordResult;
import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordUseCase;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

// 로그인 고객의 현재 비밀번호를 확인하고 신규 비밀번호로 변경한다.
@RestController
@RequiredArgsConstructor
public class LoginPasswordChangeController {
    private static final String ENDPOINT = "PUT /customers/me/password";

    private final ChangeLoginPasswordUseCase changeLoginPasswordUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;
    private final ClientIpResolver clientIpResolver;

    @PutMapping("/customers/me/password")
    public ResponseEntity<ApiResponse<LoginPasswordChangeResponse>> change(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody LoginPasswordChangeRequest request,
            HttpServletRequest httpRequest) {
        AuthenticatedCustomer customer = currentCustomerProvider.getCurrentCustomer();
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customer.customerId(),
                ENDPOINT,
                IdempotencyFingerprint.of(customer.customerId(), request),
                new TypeReference<>() {},
                () -> {
                    ChangeLoginPasswordResult result = changeLoginPasswordUseCase.change(request.toCommand(
                            customer.customerId(), customer.userId(), clientIpResolver.resolve(httpRequest)));
                    return ApiResponse.success(LoginPasswordChangeResponse.from(result), "비밀번호가 변경되었습니다.");
                });
    }
}
