package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.VerifyEmailCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// 사용자가 입력한 6자리 이메일 인증번호를 전달한다.
public record VerifyEmailRequest(
        @Schema(
                description = "사용자가 입력한 이메일 인증번호",
                example = "123456",
                pattern = "^\\d{6}$",
                accessMode = Schema.AccessMode.WRITE_ONLY
        )
        @NotBlank String verificationCode
) {

    public VerifyEmailCommand toCommand(String emailVerificationId) {
        return new VerifyEmailCommand(
                emailVerificationId,
                verificationCode
        );
    }
}
