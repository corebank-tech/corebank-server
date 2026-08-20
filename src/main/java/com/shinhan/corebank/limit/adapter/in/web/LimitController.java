package com.shinhan.corebank.limit.adapter.in.web;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.limit.application.port.in.LimitQueryUseCase;
import com.shinhan.corebank.limit.application.port.in.dto.LimitResult;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfer-limits")
@RequiredArgsConstructor
public class LimitController implements LimitControllerDocs {

    private final LimitQueryUseCase limitQueryUseCase;

    /**
     * 세션 고객 본인의 이체한도와 당일 사용 현황을 조회한다(REQ-TRSF-024).
     * 클라이언트가 보낸 고객 식별자는 신뢰하지 않는다(REQ-NFR-007).
     */
    @Override
    @GetMapping
    public ApiResponse<LimitResponse> getTransferLimit(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        LimitResult result = limitQueryUseCase.get(customer.customerId());

        return ApiResponse.success(LimitMapper.toResponse(result));
    }
}
