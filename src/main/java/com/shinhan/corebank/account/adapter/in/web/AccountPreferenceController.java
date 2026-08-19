package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderCommand;
import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderResult;
import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderUseCase;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/account-preferences")
@RequiredArgsConstructor
public class AccountPreferenceController {

    private static final String SAVE_SUCCESS_MESSAGE =
            "계좌 표시순서가 저장되었습니다.";

    private static final String RESET_SUCCESS_MESSAGE =
            "계좌 표시순서가 초기화되었습니다.";

    private final AccountDisplayOrderUseCase
            accountDisplayOrderUseCase;

    private final CurrentCustomerProvider
            currentCustomerProvider;

    private final IdempotentRequestExecutor
            idempotentRequestExecutor;

    @PutMapping("/display-order")
    public ResponseEntity<
            ApiResponse<AccountDisplayOrderResponse>>
    saveDisplayOrder(
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @RequestBody
            AccountDisplayOrderRequest request
    ) {
        Long customerId =
                currentCustomerProvider
                        .getCurrentCustomerId();

        validateRequest(request);

        String endpoint =
                "PUT /account-preferences/display-order";

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                endpoint,
                saveFingerprint(
                        customerId,
                        request
                ),
                new TypeReference<>() {
                },
                () -> {
                    AccountDisplayOrderResult result =
                            accountDisplayOrderUseCase
                                    .saveDisplayOrder(
                                            new AccountDisplayOrderCommand(
                                                    customerId,
                                                    request.accountIds()
                                            )
                                    );

                    return ApiResponse.success(
                            AccountDisplayOrderResponse
                                    .from(result),
                            SAVE_SUCCESS_MESSAGE
                    );
                }
        );
    }

    @DeleteMapping("/display-order")
    public ResponseEntity<
            ApiResponse<AccountDisplayOrderResponse>>
    resetDisplayOrder(
            @RequestHeader("Idempotency-Key")
            String idempotencyKey
    ) {
        Long customerId =
                currentCustomerProvider
                        .getCurrentCustomerId();

        String endpoint =
                "DELETE /account-preferences/display-order";

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                endpoint,
                resetFingerprint(customerId),
                new TypeReference<>() {
                },
                () -> {
                    AccountDisplayOrderResult result =
                            accountDisplayOrderUseCase
                                    .resetDisplayOrder(
                                            customerId
                                    );

                    return ApiResponse.success(
                            AccountDisplayOrderResponse
                                    .from(result),
                            RESET_SUCCESS_MESSAGE
                    );
                }
        );
    }

    private void validateRequest(
            AccountDisplayOrderRequest request
    ) {
        if (request == null
                || request.accountIds() == null) {
            throw new BusinessException(
                    CommonErrorCode.REQUIRED_FIELD_MISSING
            );
        }

        boolean invalidAccountId =
                request.accountIds()
                        .stream()
                        .anyMatch(accountId ->
                                accountId == null
                                        || accountId <= 0
                        );

        if (invalidAccountId) {
            throw new BusinessException(
                    CommonErrorCode.INVALID_INPUT
            );
        }
    }

    private Map<String, Object> saveFingerprint(
            Long customerId,
            AccountDisplayOrderRequest request
    ) {
        Map<String, Object> fingerprint =
                new LinkedHashMap<>();

        fingerprint.put(
                "customerId",
                customerId
        );

        fingerprint.put(
                "accountIds",
                request.accountIds()
        );

        return fingerprint;
    }

    private Map<String, Object> resetFingerprint(
            Long customerId
    ) {
        Map<String, Object> fingerprint =
                new LinkedHashMap<>();

        fingerprint.put(
                "customerId",
                customerId
        );

        return fingerprint;
    }
}