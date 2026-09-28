package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.ResetPasswordCommand;
import io.swagger.v3.oas.annotations.media.Schema;

public record PasswordResetRequest(
        @Schema(
                        description = "이메일로 발급된 숫자 6자리 인증번호",
                        example = "498210",
                        writeOnly = true,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String verificationCode,
        @Schema(
                        description = "신규 로그인 비밀번호(8~15자, 영문자·숫자·특수문자 포함 및 금칙 규칙 적용)",
                        example = "NewPassword1!",
                        writeOnly = true,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String newPassword,
        @Schema(
                        description = "신규 로그인 비밀번호 확인",
                        example = "NewPassword1!",
                        writeOnly = true,
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String newPasswordConfirm) {
    ResetPasswordCommand toCommand(String id) {
        return new ResetPasswordCommand(id, verificationCode, newPassword, newPasswordConfirm);
    }
}
