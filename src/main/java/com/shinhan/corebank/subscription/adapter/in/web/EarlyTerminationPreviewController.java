package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.subscription.api.TerminationPreviewUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "중도해지", description = "예·적금 중도해지 및 예상 원리금 조회")
@Validated
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.subscription.early-termination", name = "enabled", havingValue = "true")
public class EarlyTerminationPreviewController {

    private final TerminationPreviewUseCase previewUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;

    @GetMapping("/{accountId}/early-termination/preview")
    @Operation(
            operationId = "previewEarlyTermination",
            summary = "중도해지 예상 원리금 조회",
            description = "만기 전 본인 예·적금 계좌의 중도해지 예상 지급액을 조회합니다. " + "중도해지이율을 적용하며 실제 해지는 실행하지 않습니다.")
    public ResponseEntity<ApiResponse<TerminationPreviewResponse>> preview(@PathVariable @Positive Long accountId) {

        Long customerId = currentCustomerProvider.getCurrentCustomerId();

        var result = previewUseCase.previewEarly(customerId, accountId);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(TerminationPreviewResponse.from(result)));
    }
}
