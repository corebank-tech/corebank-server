package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.customer.application.port.in.CustomerInfoQueryUseCase;
import com.shinhan.corebank.customer.application.port.in.CustomerInfoResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인 고객의 기본정보 조회 API를 제공한다.
@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerInfoController {

    private final CustomerInfoQueryUseCase customerInfoQueryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @GetMapping("/me")
    public ApiResponse<CustomerInfoResponse> getCustomerInfo() {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        CustomerInfoResult result =
                customerInfoQueryUseCase.getCustomerInfo(customerId);

        return ApiResponse.success(CustomerInfoResponse.from(result));
    }
}
