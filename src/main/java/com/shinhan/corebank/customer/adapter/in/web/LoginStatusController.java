package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.customer.application.port.in.LoginStatusQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.LoginStatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class LoginStatusController {
    private final LoginStatusQueryUseCase loginStatusQueryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @GetMapping("/login-status")
    public ApiResponse<LoginStatusResponse> getLoginStatus() {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        LoginStatusResult result = loginStatusQueryUseCase.getLoginStatus(customerId);

        return ApiResponse.success(LoginStatusResponse.from(result));
    }
}
