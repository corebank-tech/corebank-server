package com.shinhan.corebank.signup.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.signup.adapter.in.web.dto.CompleteSignupRequest;
import com.shinhan.corebank.signup.adapter.in.web.dto.CompleteSignupResponse;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupCommand;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupResult;
import com.shinhan.corebank.signup.application.port.in.CompleteSignupUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

import java.util.Map;

// 회원가입 최종 완료 HTTP API를 제공한다.
@Tag(
        name = "회원가입",
        description = "회원가입 단계별 인증·입력 검증·가입 완료 API"
)
@RestController
@RequestMapping("/auth/signup")
public class SignupCompletionController {

    private static final String ENDPOINT = "POST /auth/signup/complete";

    private final CompleteSignupUseCase completeSignupUseCase;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    public SignupCompletionController(
            CompleteSignupUseCase completeSignupUseCase,
            IdempotentRequestExecutor idempotentRequestExecutor
    ) {
        this.completeSignupUseCase = completeSignupUseCase;
        this.idempotentRequestExecutor = idempotentRequestExecutor;
    }

    @Operation(
            operationId = "completeSignup",
            summary = "회원가입 완료",
            description = "tempSignupToken을 소비하고 고객·약관 동의·기존 은행 계좌를 원자적으로 등록한다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "회원가입 완료"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "CMN0001 임시 가입 토큰 오류 또는 CMN0002 멱등키 누락",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "ATH0301·ATH0302 중복 또는 CMN0301·CMN0302 멱등성 충돌",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<CompleteSignupResponse>> complete(
            @Parameter(
                    description = "UUID v4 멱등키",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CompleteSignupRequest request
    ) {
        return idempotentRequestExecutor.executeAnonymous(
                idempotencyKey,
                ENDPOINT,
                Map.of("tempSignupToken", request.tempSignupToken()),
                new TypeReference<>() {
                },
                CompleteSignupResponse::customerId,
                () -> {
                    CompleteSignupResult result = completeSignupUseCase
                            .complete(new CompleteSignupCommand(
                                    request.tempSignupToken()
                            ));
                    return ApiResponse.success(
                            CompleteSignupResponse.from(result),
                            "회원가입이 완료되었습니다."
                    );
                }
        );
    }
}
