package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionExecuteUseCase.ProductSubscriptionExecuteResult;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionQueryUseCase;
import com.shinhan.corebank.subscription.application.port.in.ProductSubscriptionValidationUseCase;
import com.shinhan.corebank.subscription.domain.ProductSubscriptionResult;
import com.shinhan.corebank.subscription.domain.SubscriptionValidation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;

@Tag(name = "상품가입", description = "상품가입 사전검증·실행·결과조회 API")
@Validated
@RestController
@RequestMapping("/product-subscriptions")
@RequiredArgsConstructor
public class ProductSubscriptionController {

    private final ProductSubscriptionValidationUseCase productSubscriptionValidationUseCase;
    private final ProductSubscriptionQueryUseCase productSubscriptionQueryUseCase;
    private final ProductSubscriptionExecuteUseCase productSubscriptionExecuteUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping("/validation")
    @Operation(
            operationId = "validateProductSubscription",
            summary = "상품가입 사전검증",
            description =
                    """
                    실제 가입 실행 전에 가입금액·기간·약관동의·잔액 등 가입 조건을 미리 검증한다.
                    검증 실패는 예외로 던지지 않고 200 + valid=false + violations[]로 응답한다 —
                    필드별 오류를 화면에 동시에 표시해야 해서 하나만 틀려도 400을 던지면 나머지 결과를 알 수 없기 때문이다.
                    productId·withdrawalAccountId·agreedTerms[].termsId처럼 참조 자체가 무효한 경우만 예외로 던진다.
                    satisfiedConditionCodes는 클라이언트가 신고한 우대조건 코드를 그대로 신뢰해 미리보기 금리에 반영한다 —
                    실제 가입 실행(POST /product-subscriptions)에서는 이 값을 받지 않고 우대금리를 적용하지 않는다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "검증 완료(가입 가능 여부는 응답의 valid로 판단)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`PRD0201` 상품을 찾을 수 없음 · `ACC0201` 출금계좌를 찾을 수 없거나 접근할 수 없음 · "
                        + "`PRD0202` agreedTerms에 이 상품과 연결되지 않은 termsId가 있음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<ProductSubscriptionValidationResponse> validate(
            @RequestBody @Valid ProductSubscriptionValidationRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        SubscriptionValidation result = productSubscriptionValidationUseCase.validate(request.toCommand(customerId));
        return ApiResponse.success(ProductSubscriptionValidationResponse.from(result));
    }

    @GetMapping("/{subscriptionId}")
    @Operation(
            operationId = "getProductSubscriptionDetail",
            summary = "상품가입 결과 조회",
            description =
                    """
                    가입 처리 결과(적용금리·만기일·예상 만기금액 등)를 조회한다.
                    정기적금이고 가입이 성공했으면 응답에 autoTransferPrefill이 함께 담겨,
                    화면이 이 값으로 자동이체 등록을 이어서 제안할 수 있다. 정기예금이거나 가입이 실패했으면 null이다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "가입 결과 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`PRD0203` 가입 내역을 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "`PRD9003` 가입 건에 연결된 계좌 정보를 찾을 수 없음. " + "FK와 상위 검증이 보장하므로 나오면 데이터 결함이다",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<ProductSubscriptionResultResponse> getProductSubscriptions(
            @Parameter(description = "조회할 가입 건의 내부 식별자", required = true, example = "1") @PathVariable @Positive
                    Long subscriptionId) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        ProductSubscriptionResult result = productSubscriptionQueryUseCase.getResult(subscriptionId, customerId);
        return ApiResponse.success(ProductSubscriptionResultResponse.from(result));
    }

    // Idempotency-Key 필수 — api_conventions.md §7-3, "Y" 적용대상에 "상품가입 실행" 명시
    // 계좌비밀번호 인증(accountPasswordAuthToken)은 P6 실구현 전까지 ProductSubscriptionAuthTokenMockAdapter가
    // 항상 통과시킨다 — 현재는 이 토큰이 무효해도 403으로 거부되지 않는다. 실제 검증이 붙으면 APW0102를
    // 응답 목록에 추가해야 한다.
    @PostMapping
    @Operation(
            operationId = "createProductSubscription",
            summary = "상품가입 실행",
            description =
                    """
                    신규 계좌를 개설하고 상품에 가입한다. 계좌비밀번호 인증 토큰과 OTP 인증 토큰을 모두 요구한다(2단계 인증).
                    가입금액·기간·약관동의 등은 사전검증(POST /product-subscriptions/validation)과 동일한 조건을
                    실행 시점에 다시 검증하며, 여기서는 검증 실패가 곧 요청 거부라 400으로 던진다(violations로 응답하지 않음).
                    정기예금(DEPOSIT)은 가입 시점에 초입금이 기표되고, 정기적금(SAVINGS)은 다음 회차부터 자동이체로 납입한다.
                    동일한 Idempotency-Key와 동일한 요청 내용으로 재요청하면 새로 처리하지 않고 저장된 응답을 반환한다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "상품가입 실행 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`APW0002` 신규 비밀번호와 확인값 불일치 · "
                        + "`PRD0001`~`PRD0007` 가입조건 검증 실패(가입금액·기간·약관동의·판매상태 등) · "
                        + "`LMT0001` 출금가능금액 부족",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`OTP0101` OTP 인증 토큰이 무효·만료·사용됨 · " + "`OTP0102` 인증한 거래 내용과 요청한 가입내용이 다름",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`PRD0201` 상품을 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`PRD0301` 1인 1계좌 제한 상품에 이미 가입함 · `CMN0301`/`CMN0302` 멱등키 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<ProductSubscriptionExecuteResponse>> execute(
            @Parameter(
                            description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody @Valid ProductSubscriptionExecuteRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                "POST /product-subscriptions",
                fingerprint(request),
                new TypeReference<>() {},
                () -> {
                    ProductSubscriptionExecuteResult result =
                            productSubscriptionExecuteUseCase.execute(request.toCommand(customerId));
                    return ApiResponse.success(ProductSubscriptionExecuteResponse.from(result), "상품 가입이 완료되었습니다.");
                });
    }

    // 멱등키 해시는 *AuthToken으로 끝나는 일회성 인증 토큰만 제외한다 — OTP·계좌비밀번호 인증 토큰을
    // 재발급받아 재시도해도 같은 요청으로 인식되도록(idempotency_key.request_hash 컬럼 코멘트 참고).
    // newAccountPassword/Confirm은 토큰이 아니라 실제 요청 값이라 포함한다.
    private Map<String, Object> fingerprint(ProductSubscriptionExecuteRequest request) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("productId", request.productId());
        fingerprint.put("subscriptionAmount", request.subscriptionAmount());
        fingerprint.put("termMonths", request.termMonths());
        fingerprint.put("withdrawalAccountId", request.withdrawalAccountId());
        fingerprint.put("newAccountPassword", request.newAccountPassword());
        fingerprint.put("newAccountPasswordConfirm", request.newAccountPasswordConfirm());
        fingerprint.put("agreedTerms", request.agreedTerms());
        return fingerprint;
    }
}
