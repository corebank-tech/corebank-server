package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountAliasResult;
import com.shinhan.corebank.account.application.port.in.AccountAliasUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewQueryUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.common.response.ApiResponse;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

import java.util.function.Supplier;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountOverviewQueryUseCase accountOverviewQueryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final AccountAliasUseCase accountAliasUseCase;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ApiResponse<AccountOverviewResponse> getAccounts() {
        Long customerId =
                currentCustomerProvider.getCurrentCustomerId();

        AccountOverviewResult result =
                accountOverviewQueryUseCase
                        .getOverview(customerId);

        return ApiResponse.success(
                AccountOverviewResponse.from(result)
        );
    }

    @PutMapping("/{accountId}/alias")
    public ResponseEntity<ApiResponse<AccountAliasResponse>>
    changeAlias(
            @PathVariable
            @Positive
            Long accountId,

            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @RequestBody
            AccountAliasChangeRequest request
    ) {
        Long customerId =
                currentCustomerProvider
                        .getCurrentCustomerId();

        String endpoint =
                "PUT /accounts/"
                        + accountId
                        + "/alias";

        return withIdempotency(
                idempotencyKey,
                customerId,
                endpoint,
                changeFingerprint(
                        customerId,
                        accountId,
                        request
                ),
                new TypeReference<>() {
                },
                () -> {
                    AccountAliasResult result =
                            accountAliasUseCase
                                    .changeAlias(
                                            customerId,
                                            accountId,
                                            request.alias()
                                    );

                    return ApiResponse.success(
                            AccountAliasResponse.from(
                                    result
                            )
                    );
                }
        );
    }

    @DeleteMapping("/{accountId}/alias")
    public ResponseEntity<ApiResponse<Void>>
    deleteAlias(
            @PathVariable
            @Positive
            Long accountId,

            @RequestHeader("Idempotency-Key")
            String idempotencyKey
    ) {
        Long customerId =
                currentCustomerProvider
                        .getCurrentCustomerId();

        String endpoint =
                "DELETE /accounts/"
                        + accountId
                        + "/alias";

        return withIdempotency(
                idempotencyKey,
                customerId,
                endpoint,
                deleteFingerprint(
                        customerId,
                        accountId
                ),
                new TypeReference<>() {
                },
                () -> {
                    accountAliasUseCase
                            .deleteAlias(
                                    customerId,
                                    accountId
                            );

                    return ApiResponse.success();
                }
        );
    }

    private Map<String, Object> changeFingerprint(
            Long customerId,
            Long accountId,
            AccountAliasChangeRequest request
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

        fingerprint.put(
                "alias",
                request.alias()
        );

        return fingerprint;
    }

    private Map<String, Object> deleteFingerprint(
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

    private <T> ResponseEntity<ApiResponse<T>>
    withIdempotency(
            String idempotencyKey,
            Long customerId,
            String endpoint,
            Object fingerprint,
            TypeReference<ApiResponse<T>>
                    responseType,
            Supplier<ApiResponse<T>> action
    ) {
        IdempotencyResult idempotencyResult =
                idempotencyService.begin(
                        idempotencyKey,
                        customerId,
                        endpoint,
                        toJson(fingerprint)
                );

        if (idempotencyResult.replay()) {
            return ResponseEntity
                    .status(
                            idempotencyResult
                                    .httpStatus()
                    )
                    .body(
                            fromJson(
                                    idempotencyResult
                                            .responseSnapshot(),
                                    responseType
                            )
                    );
        }

        ApiResponse<T> response;

        try {
            response = action.get();
        } catch (RuntimeException e) {
            idempotencyService.release(
                    idempotencyKey
            );

            throw e;
        }

        idempotencyService.complete(
                idempotencyKey,
                (short) HttpStatus.OK.value(),
                toJson(response)
        );

        return ResponseEntity.ok(response);
    }

    private String toJson(Object value) {
        try {
            return objectMapper
                    .writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "요청/응답을 JSON으로 직렬화하지 못했습니다.",
                    e
            );
        }
    }

    private <T> T fromJson(
            String json,
            TypeReference<T> type
    ) {
        try {
            return objectMapper
                    .readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "저장된 응답을 역직렬화하지 못했습니다.",
                    e
            );
        }
    }

}