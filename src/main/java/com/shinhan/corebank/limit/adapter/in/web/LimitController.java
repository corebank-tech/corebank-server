package com.shinhan.corebank.limit.adapter.in.web;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.limit.application.port.in.LimitCommandUseCase;
import com.shinhan.corebank.limit.application.port.in.LimitQueryUseCase;
import com.shinhan.corebank.limit.application.port.in.dto.LimitResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/transfer-limits")
@RequiredArgsConstructor
public class LimitController implements LimitControllerDocs {

    private final LimitQueryUseCase limitQueryUseCase;
    private final LimitCommandUseCase limitCommandUseCase;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    /**
     * 세션 고객 본인의 이체한도와 당일 사용 현황을 조회한다(REQ-TRSF-024).
     * 클라이언트가 보낸 고객 식별자는 신뢰하지 않는다(REQ-NFR-007).
     */
    @Override
    @GetMapping
    public ApiResponse<LimitResponse> getTransferLimit(@AuthenticationPrincipal AuthenticatedCustomer customer) {
        LimitResult result = limitQueryUseCase.get(customer.customerId());

        return ApiResponse.success(LimitResponse.from(result));
    }

    /** 세션 고객 본인의 1회·1일 이체한도를 함께 교체한다(REQ-TRSF-025). */
    @Override
    @PutMapping
    public ResponseEntity<ApiResponse<LimitResponse>> updateTransferLimit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody LimitUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedCustomer customer) {
        Long customerId = customer.customerId();

        return idempotentRequestExecutor.execute(
                idempotencyKey, customerId, "PUT /transfer-limits",
                IdempotencyFingerprint.of(customerId, request),
                new TypeReference<>() {},
                () -> ApiResponse.success(
                        LimitResponse.from(limitCommandUseCase.update(customerId, request.toCommand()))));
    }
}
