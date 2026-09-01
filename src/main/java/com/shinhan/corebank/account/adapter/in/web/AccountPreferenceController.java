package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderCommand;
import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderResult;
import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderUseCase;
import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/account-preferences")
@RequiredArgsConstructor
@Tag(name = "계좌 설정", description = "계좌 표시순서 설정 API")
public class AccountPreferenceController {

    private static final String SAVE_SUCCESS_MESSAGE = "계좌 표시순서가 저장되었습니다.";

    private static final String RESET_SUCCESS_MESSAGE = "계좌 표시순서가 초기화되었습니다.";

    private final AccountDisplayOrderUseCase accountDisplayOrderUseCase;

    private final CurrentCustomerProvider currentCustomerProvider;

    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PutMapping("/display-order")
    @Operation(
            operationId = "saveAccountDisplayOrder",
            summary = "계좌 표시순서 저장",
            description =
                    """
                    전체 계좌조회 화면에서 사용할 계좌 표시순서를 저장한다.
                    accountIds는 로그인 고객이 소유한 계좌 ID로 구성해야 한다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "계좌 표시순서 저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 잘못된 입력값 · `CMN0002` 필수 입력값/Idempotency-Key 누락 · `ACC0002` 올바르지 않은 표시순서",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`ACC0201` 계좌를 찾을 수 없거나 접근할 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`CMN0301`/`CMN0302` 멱등키 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<AccountDisplayOrderResponse>> saveDisplayOrder(
            @Parameter(
                            description = "멱등키. 동일 키와 동일 요청 재요청 시 저장된 응답 반환",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody AccountDisplayOrderRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();

        validateRequest(request);

        String endpoint = "PUT /account-preferences/display-order";

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                endpoint,
                saveFingerprint(customerId, request),
                new TypeReference<>() {},
                () -> {
                    AccountDisplayOrderResult result = accountDisplayOrderUseCase.saveDisplayOrder(
                            new AccountDisplayOrderCommand(customerId, request.accountIds()));

                    return ApiResponse.success(AccountDisplayOrderResponse.from(result), SAVE_SUCCESS_MESSAGE);
                });
    }

    @DeleteMapping("/display-order")
    @Operation(
            operationId = "resetAccountDisplayOrder",
            summary = "계좌 표시순서 초기화",
            description = "저장된 계좌 표시순서를 삭제하고 기본 표시순서로 초기화한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "계좌 표시순서 초기화 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0002` 필수 Idempotency-Key 누락",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`CMN0301`/`CMN0302` 멱등키 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<AccountDisplayOrderResponse>> resetDisplayOrder(
            @Parameter(
                            description = "멱등키. 동일 키와 동일 요청 재요청 시 저장된 응답 반환",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();

        String endpoint = "DELETE /account-preferences/display-order";

        return idempotentRequestExecutor.execute(
                idempotencyKey, customerId, endpoint, resetFingerprint(customerId), new TypeReference<>() {}, () -> {
                    AccountDisplayOrderResult result = accountDisplayOrderUseCase.resetDisplayOrder(customerId);

                    return ApiResponse.success(AccountDisplayOrderResponse.from(result), RESET_SUCCESS_MESSAGE);
                });
    }

    private void validateRequest(AccountDisplayOrderRequest request) {
        if (request == null || request.accountIds() == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        boolean invalidAccountId =
                request.accountIds().stream().anyMatch(accountId -> accountId == null || accountId <= 0);

        if (invalidAccountId) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private Map<String, Object> saveFingerprint(Long customerId, AccountDisplayOrderRequest request) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();

        fingerprint.put("customerId", customerId);

        fingerprint.put("accountIds", request.accountIds());

        return fingerprint;
    }

    private Map<String, Object> resetFingerprint(Long customerId) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();

        fingerprint.put("customerId", customerId);

        return fingerprint;
    }
}
