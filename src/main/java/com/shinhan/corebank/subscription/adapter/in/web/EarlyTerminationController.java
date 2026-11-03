package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.subscription.api.EarlyTerminationCommand;
import com.shinhan.corebank.subscription.api.EarlyTerminationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

/**
 * PH-16 예·적금 중도해지 API.
 * 운영 활성화 전 최종 검증이 필요하므로 기본값은 false로 유지한다.
 */
@Validated
@Tag(name = "중도해지", description = "예·적금 중도해지 및 원리금 지급 API")
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.subscription.early-termination", name = "enabled", havingValue = "true")
public class EarlyTerminationController {

    private final EarlyTerminationUseCase earlyTerminationUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping("/{accountId}/early-termination")
    @Operation(
            operationId = "earlyTerminateAccount",
            summary = "예·적금 중도해지 실행",
            description = "본인 소유 ACTIVE 예·적금 계좌를 중도해지합니다. "
                    + "가입 시 등록한 수취계좌로 원리금을 이체하며, "
                    + "Idempotency-Key와 Account-Password-Auth-Token이 필요합니다.")
    public ResponseEntity<ApiResponse<EarlyTerminationResponse>> terminate(
            @PathVariable @Positive Long accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("Account-Password-Auth-Token") String accountPasswordAuthToken) {

        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        String endpoint = "POST /accounts/" + accountId + "/early-termination";
        return idempotentRequestExecutor.execute(
                idempotencyKey, customerId, endpoint, Map.of("accountId", accountId), new TypeReference<>() {}, () -> {
                    var result = earlyTerminationUseCase.terminate(
                            new EarlyTerminationCommand(customerId, accountId, accountPasswordAuthToken));
                    return ApiResponse.success(EarlyTerminationResponse.from(result), "중도해지가 완료되었습니다.");
                });
    }
}
