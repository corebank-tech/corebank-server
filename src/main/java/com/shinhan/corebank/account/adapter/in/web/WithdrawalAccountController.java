package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterResult;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterUseCase;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/withdrawal-accounts")
@RequiredArgsConstructor
@Validated
public class WithdrawalAccountController {

    private static final String REGISTER_SUCCESS_MESSAGE =
            "출금계좌가 등록되었습니다.";

    private final WithdrawalAccountRegisterUseCase
            withdrawalAccountRegisterUseCase;

    private final CurrentCustomerProvider
            currentCustomerProvider;

    private final IdempotentRequestExecutor
            idempotentRequestExecutor;

    @PutMapping("/{accountId}")
    public ResponseEntity<
            ApiResponse<WithdrawalAccountRegisterResponse>
            > register(
            @PathVariable
            @Positive
            Long accountId,

            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @Valid
            @RequestBody
            WithdrawalAccountRegisterRequest request
    ) {
        Long customerId =
                currentCustomerProvider
                        .getCurrentCustomerId();

        String endpoint =
                "PUT /withdrawal-accounts/"
                        + accountId;

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                endpoint,
                fingerprint(
                        customerId,
                        accountId
                ),
                new TypeReference<>() {
                },
                () -> {
                    WithdrawalAccountRegisterResult result =
                            withdrawalAccountRegisterUseCase
                                    .register(
                                            request.toCommand(
                                                    customerId,
                                                    accountId
                                            )
                                    );

                    return ApiResponse.success(
                            WithdrawalAccountRegisterResponse
                                    .from(result),
                            REGISTER_SUCCESS_MESSAGE
                    );
                }
        );
    }

    private Map<String, Object> fingerprint(
            Long customerId,
            Long accountId
    ) {
        Map<String, Object> fingerprint =
                new LinkedHashMap<>();

        fingerprint.put(
                "customerId",
                customerId
        );

        fingerprint.put(
                "accountId",
                accountId
        );

        return fingerprint;
    }
}