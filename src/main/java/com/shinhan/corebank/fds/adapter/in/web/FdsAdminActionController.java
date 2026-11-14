package com.shinhan.corebank.fds.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.fds.application.service.FdsAdminActionService;
import com.shinhan.corebank.fds.application.service.FdsAdminActionStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 콜 기록과 승인·거절을 PENDING 조치로 내구화하는 API. */
@RestController
@RequestMapping("/admin/fds/holds")
@RequiredArgsConstructor
@Validated
@Tag(name = "FDS 관리자 조치", description = "위험 상태 재검증 후 P4 승인·거절")
public class FdsAdminActionController {
    private final FdsAdminActionStore store;
    private final FdsAdminActionService actions;
    private final CurrentCustomerProvider currentCustomer;

    @PostMapping("/{transferHoldId}/call-record")
    @Operation(
            operationId = "recordFdsAdminCallResult",
            summary = "고객 콜 결과 추가",
            description = "CUSTOMER_WRITE와 CSRF, Idempotency-Key가 필요하며 기존 기록을 수정하지 않고 추가합니다.")
    public ApiResponse<FdsAdminActionStore.CallRecord> call(
            @PathVariable @Positive long transferHoldId,
            @RequestHeader(name = "Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestHeader(name = AccessReason.HEADER) AccessReason accessReason,
            @RequestBody @Valid CallRequest request) {
        return ApiResponse.success(store.recordCall(
                transferHoldId,
                idempotencyKey,
                currentCustomer.getCurrentCustomerId(),
                request.result(),
                request.investigationReason()));
    }

    @PostMapping("/{transferHoldId}/approve")
    @Operation(
            operationId = "approveFdsHeldTransfer",
            summary = "보류 승인",
            description =
                    "CUSTOMER_WRITE와 CSRF, Idempotency-Key가 필요합니다. NO_MATCH·UNAVAILABLE이고 최신 CUSTOMER_CONFIRMED 콜이 있어야 합니다. 콜 조건 실패는 FDS0302, 충돌로 확정된 요청과 재전달은 FDS0301을 반환합니다.")
    public ApiResponse<FdsAdminActionStore.Action> approve(
            @PathVariable @Positive long transferHoldId,
            @RequestHeader(name = "Idempotency-Key") @NotBlank String idempotencyKey) {
        return ApiResponse.success(
                actions.act(transferHoldId, idempotencyKey, currentCustomer.getCurrentCustomerId(), "APPROVE", null));
    }

    @PostMapping("/{transferHoldId}/reject")
    @Operation(
            operationId = "rejectFdsHeldTransfer",
            summary = "보류 거절",
            description =
                    "CUSTOMER_WRITE와 CSRF, Idempotency-Key가 필요합니다. MATCH는 RISK_ACCOUNT_MATCH만, 그 외는 최신 CUSTOMER_DENIED 콜 뒤 거절합니다. 충돌로 확정된 요청과 재전달은 FDS0301을 반환합니다.")
    public ApiResponse<FdsAdminActionStore.Action> reject(
            @PathVariable @Positive long transferHoldId,
            @RequestHeader(name = "Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid RejectRequest request) {
        return ApiResponse.success(actions.act(
                transferHoldId,
                idempotencyKey,
                currentCustomer.getCurrentCustomerId(),
                "REJECT",
                request.reasonCode()));
    }

    public record CallRequest(
            @Schema(
                            description = "고객 확인 결과",
                            allowableValues = {"CUSTOMER_CONFIRMED", "CUSTOMER_DENIED", "NO_ANSWER", "INCONCLUSIVE"})
                    @NotBlank
                    String result,
            @Schema(description = "콜 기록 사유", maxLength = 200) @NotBlank @Size(max = 200) String investigationReason) {}

    public record RejectRequest(
            @Schema(
                            description = "거절 사유 코드",
                            allowableValues = {"RISK_ACCOUNT_MATCH", "CUSTOMER_DENIED"},
                            maxLength = 40)
                    @NotBlank
                    @Size(max = 40)
                    String reasonCode) {}
}
