package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.ChangeAccountPasswordCommand;
import com.shinhan.corebank.account.application.port.in.ChangeAccountPasswordResult;
import com.shinhan.corebank.account.application.port.in.ChangeAccountPasswordUseCase;
import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordCommand;
import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordResult;
import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordUseCase;
import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

// 계좌비밀번호 검증 API를 계좌 하위 경로로 제공한다.
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Validated
@Tag(
        name = "계좌",
        description = "계좌 조회 및 계좌비밀번호 인증 API"
)
public class AccountPasswordController {

    private static final String SUCCESS_MESSAGE =
            "계좌비밀번호 검증이 완료되었습니다.";
    private static final String CHANGE_SUCCESS_MESSAGE =
            "계좌비밀번호가 변경되었습니다.";

    private final VerifyAccountPasswordUseCase verifyUseCase;
    private final ChangeAccountPasswordUseCase changeUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping("/{accountId}/password/verify")
    @Operation(
            summary = "계좌비밀번호 검증",
            description = "로그인 고객이 소유한 계좌의 숫자 4자리 비밀번호를 검증하고 300초 일회용 인증 토큰을 발급한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "계좌비밀번호 검증 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "`CMN0001` 요청 형식 오류 · `CMN0002` 필수값 누락 · `APW0001` 비밀번호 불일치",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "`APW0101` 계좌비밀번호 5회 오류로 잠김",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "`ACC0201` 계좌를 찾을 수 없거나 접근할 수 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    public ApiResponse<AccountPasswordVerifyResponse> verify(
            @Parameter(
                    description = "계좌비밀번호를 검증할 계좌의 내부 식별자",
                    required = true,
                    example = "101"
            )
            @PathVariable
            @Positive
            Long accountId,

            @RequestBody
            AccountPasswordVerifyRequest request
    ) {
        Long customerId =
                currentCustomerProvider.getCurrentCustomerId();

        VerifyAccountPasswordResult result =
                verifyUseCase.verify(
                        new VerifyAccountPasswordCommand(
                                customerId,
                                accountId,
                                request.accountPassword()
                        )
                );

        return ApiResponse.success(
                AccountPasswordVerifyResponse.from(result),
                SUCCESS_MESSAGE
        );
    }

    @PutMapping("/{accountId}/password")
    @Operation(
            summary = "계좌비밀번호 변경",
            description = """
                    계좌비밀번호 인증 토큰과 ACCOUNT_PASSWORD_CHANGE 용도의 OTP 인증 토큰을
                    각각 검증·소비한 뒤 로그인 고객 소유 계좌의 비밀번호를 변경한다.
                    입출금·예금·적금 계좌를 모두 지원하며 동일 멱등키 재요청은 최초 응답을 재생한다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "계좌비밀번호 변경 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "`CMN0001` 요청 형식 오류 · `CMN0002` 필수값 누락 · `APW0002` 신규 비밀번호 확인 불일치",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "`APW0101` 비밀번호 오류 잠금 · `APW0102` 계좌비밀번호 인증 토큰 오류 · `OTP0101` OTP 인증 토큰 오류 · `OTP0102` OTP 거래정보 불일치",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "`ACC0201` 계좌를 찾을 수 없거나 접근할 수 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "`ACC0301` 거래정지·해지 계좌 · `CMN0301`/`CMN0302` 멱등키 충돌",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<AccountPasswordChangeResponse>>
    change(
            @Parameter(
                    description = "비밀번호를 변경할 계좌의 내부 식별자",
                    required = true,
                    example = "101"
            )
            @PathVariable
            @Positive
            Long accountId,

            @Parameter(
                    description = "UUID v4 멱등키",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @RequestBody
            AccountPasswordChangeRequest request,

            HttpServletRequest servletRequest
    ) {
        Long customerId =
                currentCustomerProvider.getCurrentCustomerId();
        String endpoint =
                "PUT /accounts/" + accountId + "/password";

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                endpoint,
                changeFingerprint(customerId, accountId, request),
                new TypeReference<>() {
                },
                () -> {
                    ChangeAccountPasswordResult result =
                            changeUseCase.change(
                                    new ChangeAccountPasswordCommand(
                                            customerId,
                                            accountId,
                                            request.otpAuthToken(),
                                            request.accountPasswordAuthToken(),
                                            request.newAccountPassword(),
                                            request.newAccountPasswordConfirm(),
                                            servletRequest.getRemoteAddr()
                                    )
                            );

                    return ApiResponse.success(
                            AccountPasswordChangeResponse.from(result),
                            CHANGE_SUCCESS_MESSAGE
                    );
                }
        );
    }

    // 일회성 인증 토큰은 제외하고 실제 변경 대상과 신규 비밀번호만 멱등 지문에 포함한다.
    private Map<String, Object> changeFingerprint(
            Long customerId,
            Long accountId,
            AccountPasswordChangeRequest request
    ) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("customerId", customerId);
        fingerprint.put("accountId", accountId);
        fingerprint.put(
                "newAccountPassword",
                request.newAccountPassword()
        );
        fingerprint.put(
                "newAccountPasswordConfirm",
                request.newAccountPasswordConfirm()
        );
        return fingerprint;
    }
}
