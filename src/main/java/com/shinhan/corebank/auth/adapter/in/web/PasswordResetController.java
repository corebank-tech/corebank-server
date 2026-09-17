package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.*;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(operationId = "issuePasswordReset", summary = "비밀번호 재설정 인증번호 발급")
    public ApiResponse<PasswordResetIssueResponse> issue(@Valid @RequestBody PasswordResetIssueRequest request) {
        return ApiResponse.success(
                PasswordResetIssueResponse.from(issueUseCase.issue(request.toCommand())), "비밀번호 재설정 인증번호가 발급되었습니다.");
    }

    @PutMapping("/{passwordResetRequestId}")
    @Operation(operationId = "resetPassword", summary = "비밀번호 재설정")
    public ResponseEntity<ApiResponse<PasswordResetResponse>> reset(
            @PathVariable String passwordResetRequestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PasswordResetRequest request) {
        Long customerId = resetUseCase.resolveCustomerId(passwordResetRequestId);
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                "PUT /auth/password-reset-requests/" + passwordResetRequestId,
                Map.of(
                        "customerId",
                        customerId,
                        "passwordResetRequestId",
                        passwordResetRequestId,
                        "verificationCode",
                        request.verificationCode(),
                        "newPassword",
                        request.newPassword(),
                        "newPasswordConfirm",
                        request.newPasswordConfirm()),
                new TypeReference<>() {},
                () -> {
                    ResetPasswordResult result = resetUseCase.reset(request.toCommand(passwordResetRequestId));
                    return ApiResponse.success(new PasswordResetResponse(result.changedAt()), "비밀번호가 재설정되었습니다.");
                });
    }
}
