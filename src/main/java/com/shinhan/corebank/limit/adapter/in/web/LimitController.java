package com.shinhan.corebank.limit.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.limit.application.port.in.LimitQueryUseCase;
import com.shinhan.corebank.limit.application.port.in.dto.LimitResult;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfer-limits")
@RequiredArgsConstructor
public class LimitController {

    private final LimitQueryUseCase limitQueryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    /**
     * 세션 고객 본인의 이체한도와 당일 사용 현황을 조회한다(REQ-TRSF-024).
     * 클라이언트가 보낸 고객 식별자는 신뢰하지 않는다(REQ-NFR-007).
     */
    @GetMapping
    public ApiResponse<LimitResponse> getTransferLimit() {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        LimitResult result = limitQueryUseCase.get(customerId);

        return ApiResponse.success(LimitMapper.toResponse(result));
    }
}
