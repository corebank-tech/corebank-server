package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.transfer.api.TransferHoldUseCase;
import com.shinhan.corebank.transfer.application.port.in.DelayedTransferSettingUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
@Tag(name = "지연이체", description = "지연이체 설정 조회·변경, 보류된 이체 취소 (PH-06)")
public class DelayedTransferController {

    private final DelayedTransferSettingUseCase delayedTransferSettingUseCase;
    private final TransferHoldUseCase transferHoldUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @GetMapping("/delayed-transfer-setting")
    @Operation(
            operationId = "getDelayedTransferSetting",
            summary = "지연이체 설정 조회",
            description = "저장한 적 없으면 꺼짐(enabled=false)과 기본값(기준금액 1,000,000원, 지연시간 3시간)을 돌려준다.")
    public ResponseEntity<ApiResponse<DelayedTransferSettingResponse>> get() {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return ResponseEntity.ok(ApiResponse.success(
                DelayedTransferSettingResponse.from(delayedTransferSettingUseCase.get(customerId))));
    }

    @PutMapping("/delayed-transfer-setting")
    @Operation(
            operationId = "updateDelayedTransferSetting",
            summary = "지연이체 설정 변경",
            description =
                    """
            켠 상태에서 보호를 약하게 하는 변경(끄기·기준금액 올리기·지연시간 줄이기)은 계좌비밀번호 인증 토큰이 필요하다. \
            토큰이 없으면 `CMN0002`, 무효면 `APW0102`다. 켜기·기준금액 내리기·지연시간 늘리기는 토큰 없이 된다. \
            기준금액은 1원 이상, 지연시간은 3~24시간이고 벗어나면 `TRF0009`다.""")
    public ResponseEntity<ApiResponse<DelayedTransferSettingResponse>> update(
            @Parameter(description = "멱등키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(description = "계좌비밀번호 인증 토큰. 보호를 약하게 하는 변경일 때만 필요")
                    @RequestHeader(value = "Account-Password-Auth-Token", required = false)
                    String authToken,
            @RequestBody DelayedTransferSettingRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                "PUT /transfers/delayed-transfer-setting",
                IdempotencyFingerprint.of(customerId, request),
                new TypeReference<>() {},
                () -> ApiResponse.success(DelayedTransferSettingResponse.from(
                        delayedTransferSettingUseCase.change(request.toCommand(customerId, authToken)))));
    }

    @PostMapping("/holds/{transferHoldId}/cancel")
    @Operation(
            operationId = "cancelTransferHold",
            summary = "보류된 이체 취소",
            description =
                    """
            즉시이체 응답의 `transferHold.transferHoldId`로 보류된 이체를 취소한다. 실행 30분 전(`cancellableUntil`)까지 되고, \
            지나면 `TRF0312`다. 돈이 나가는 것을 멈추는 동작이라 추가 인증은 받지 않는다. 남의 보류·없는 보류는 `TRF0203`, \
            이미 실행 중이거나 끝난 보류는 `TRF0311`이다.""")
    public ResponseEntity<ApiResponse<TransferHoldCancelResponse>> cancel(
            @Parameter(description = "멱등키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable long transferHoldId) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                "POST /transfers/holds/{transferHoldId}/cancel",
                IdempotencyFingerprint.of(customerId, null, Map.of("transferHoldId", transferHoldId)),
                new TypeReference<>() {},
                () -> {
                    transferHoldUseCase.cancel(transferHoldId, customerId);
                    return ApiResponse.success(new TransferHoldCancelResponse(transferHoldId, "CANCELLED"));
                });
    }
}
