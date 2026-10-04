package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.application.port.in.*;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/auth/password-reset-requests")
@RequiredArgsConstructor
@Tag(name = "인증", description = "비밀번호 재설정 API")
public class PasswordResetController {
    private final IssuePasswordResetUseCase issueUseCase;
    private final ResetPasswordUseCase resetUseCase;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping
    @Operation(
            operationId = "issuePasswordReset",
            summary = "비밀번호 재설정 인증번호 발급",
            description = "아이디·성명·가입 이메일이 모두 일치하는 고객에게 180초 동안 유효한 인증번호를 발급한다. " + "재발급하면 기존 활성 인증 요청은 즉시 무효화된다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증번호 발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 입력 형식 오류 · `CMN0002` 필수 입력값 누락",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`ATH0102` 로그인 비밀번호 5회 오류로 고객 계정 잠금",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`ATH0201` 입력한 정보와 일치하는 고객 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<PasswordResetIssueResponse> issue(@Valid @RequestBody PasswordResetIssueRequest request) {
        return ApiResponse.success(
                PasswordResetIssueResponse.from(issueUseCase.issue(request.toCommand())), "비밀번호 재설정 인증번호가 발급되었습니다.");
    }

    @PutMapping("/{passwordResetRequestId}")
    @Operation(
            operationId = "resetPassword",
            summary = "비밀번호 재설정",
            description = "인증번호와 신규 비밀번호를 검증하고 비밀번호를 변경한다. 인증 요청은 한 번만 사용할 수 있으며, "
                    + "성공하면 로그인 실패 횟수를 0으로 초기화하고 해당 고객의 기존 로그인 세션을 모두 무효화한다. "
                    + "이 API는 비로그인 경로이므로 동일 멱등키와 동일 요청의 재시도에는 최초 응답을 재생한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "비밀번호 재설정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0002` 필수 입력값 누락 · `ATH0001` 신규 비밀번호 정책 위반 · "
                        + "`ATH0002` 비밀번호 확인 불일치 · `ATH0003` 직전 비밀번호 재사용 · "
                        + "`ATH0007` 인증번호 불일치 · `ATH0008` 인증번호 만료",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`ATH0102` 로그인 비밀번호 5회 오류로 고객 계정 잠금",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`ATH0202` 인증 요청을 찾을 수 없거나 이미 사용됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`CMN0301` 동일 요청 처리 중 · `CMN0302` 동일 멱등키를 다른 요청에 사용 · `CMN0303` 동시 변경 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<PasswordResetResponse>> reset(
            @Parameter(description = "인증번호 발급 API에서 반환한 비밀번호 재설정 요청 ID", required = true, example = "PRR_test")
                    @PathVariable
                    String passwordResetRequestId,
            @Parameter(
                            description = "멱등키. 동일 키와 동일 요청으로 재시도하면 최초 응답을 반환",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Valid @RequestBody PasswordResetRequest request) {
        Long customerId = resetUseCase.resolveCustomerId(passwordResetRequestId);
        // 공용 헬퍼로 요청 본문과 경로 변수를 정렬해 재시도마다 동일한 지문을 생성한다.
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                "PUT /auth/password-reset-requests/" + passwordResetRequestId,
                IdempotencyFingerprint.of(
                        customerId, request, Map.of("passwordResetRequestId", passwordResetRequestId)),
                new TypeReference<>() {},
                () -> {
                    ResetPasswordResult result = resetUseCase.reset(request.toCommand(passwordResetRequestId));
                    return ApiResponse.success(new PasswordResetResponse(result.changedAt()), "비밀번호가 재설정되었습니다.");
                });
    }
}
