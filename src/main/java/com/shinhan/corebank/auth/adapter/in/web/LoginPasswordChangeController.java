package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordResult;
import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordUseCase;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

// 로그인 고객의 현재 비밀번호를 확인하고 신규 비밀번호로 변경한다.
@RestController
@RequiredArgsConstructor
@Tag(name = "인증", description = "로그인과 비밀번호 관리 API")
public class LoginPasswordChangeController {
    private static final String ENDPOINT = "PUT /customers/me/password";

    private final ChangeLoginPasswordUseCase changeLoginPasswordUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;
    private final ClientIpResolver clientIpResolver;

    @PutMapping("/customers/me/password")
    @Operation(
            operationId = "changeLoginPassword",
            summary = "로그인 비밀번호 변경",
            description = "로그인 고객의 현재 비밀번호를 확인한 뒤 신규 비밀번호로 변경한다. 성공하면 로그인 실패 횟수를 "
                    + "0으로 초기화하고 현재 세션을 포함한 해당 고객의 모든 로그인 세션을 무효화하므로 새 비밀번호로 재로그인해야 한다. "
                    + "성공 후 같은 멱등키로 재시도하면 만료된 세션 때문에 `401 CMN0101`이 반환될 수 있다. "
                    + "이때는 변경이 이미 반영됐을 수 있으므로 새 비밀번호로 로그인한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 비밀번호 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0002` 필수 입력값 누락 · `ATH0001` 신규 비밀번호 정책 위반 · "
                        + "`ATH0002` 비밀번호 확인 불일치 · `ATH0003` 직전 비밀번호 재사용 · `ATH0010` 현재 비밀번호 불일치",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`ATH0102` 로그인 비밀번호 5회 오류로 고객 계정 잠금",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`CMN0301` 동일 요청 처리 중 · `CMN0302` 동일 멱등키를 다른 요청에 사용 · `CMN0303` 동시 변경 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<LoginPasswordChangeResponse>> change(
            @Parameter(
                            description = "멱등키. 성공 전 동일 요청은 최초 응답을 재생하지만 성공 후에는 만료된 세션으로 401이 반환될 수 있음",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody LoginPasswordChangeRequest request,
            HttpServletRequest httpRequest) {
        AuthenticatedCustomer customer = currentCustomerProvider.getCurrentCustomer();
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customer.customerId(),
                ENDPOINT,
                IdempotencyFingerprint.of(customer.customerId(), request),
                new TypeReference<>() {},
                () -> {
                    ChangeLoginPasswordResult result = changeLoginPasswordUseCase.change(request.toCommand(
                            customer.customerId(), customer.userId(), clientIpResolver.resolve(httpRequest)));
                    return ApiResponse.success(LoginPasswordChangeResponse.from(result), "비밀번호가 변경되었습니다.");
                });
    }
}
