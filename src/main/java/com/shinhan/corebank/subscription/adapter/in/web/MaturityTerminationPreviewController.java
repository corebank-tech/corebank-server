package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.subscription.api.TerminationPreviewUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "만기해지", description = "예·적금 만기해지 및 예상 원리금 조회")
@Validated
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class MaturityTerminationPreviewController {

    private final TerminationPreviewUseCase previewUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @GetMapping("/{accountId}/maturity-termination/preview")
    @Operation(
            operationId = "previewMaturityTermination",
            summary = "만기해지 예상 원리금 조회",
            description = "만기 도래한 본인 예·적금 계좌의 예상 지급액을 조회합니다. " + "계좌 잔액이나 상태를 변경하지 않으며 실제 해지 금액은 달라질 수 있습니다.")
    public ResponseEntity<ApiResponse<TerminationPreviewResponse>> preview(@PathVariable @Positive Long accountId) {

        Long customerId = currentCustomerProvider.getCurrentCustomerId();

        var result = previewUseCase.previewMaturity(customerId, accountId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(TerminationPreviewResponse.from(result)));
    }
}
