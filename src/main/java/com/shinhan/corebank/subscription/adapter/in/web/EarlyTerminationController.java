package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.subscription.api.EarlyTerminationCommand;
import com.shinhan.corebank.subscription.api.EarlyTerminationUseCase;
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
 * 연결 이체 정리(#844) 및 동시성 통합 테스트가 끝나기 전에는 요청을 받지 않는다.
 * app.subscription.early-termination.enabled=false (기본값).
 */
@Validated
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.subscription.early-termination", name = "enabled", havingValue = "true")
public class EarlyTerminationController {

    private final EarlyTerminationUseCase earlyTerminationUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping("/{accountId}/early-termination")
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
