package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountAliasResult;
import com.shinhan.corebank.account.application.port.in.AccountAliasUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewQueryUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;

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
    private final IdempotentRequestExecutor idempotentRequestExecutor;

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

        return idempotentRequestExecutor.execute(
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

        return idempotentRequestExecutor.execute(
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
}