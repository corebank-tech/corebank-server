package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationCommand;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 인증번호를 발급할 이메일과 인증 목적을 전달한다.
public record IssueEmailVerificationRequest(
        @Schema(
                description = "인증번호를 받을 이메일",
                example = "user@mail.com",
                maxLength = 100
        )
        @NotBlank
        @Email
        @Size(max = 100)
        String email,

        @Schema(
                description = "이메일 인증 목적",
                example = "SIGN_UP"
        )
        @NotNull
        EmailVerificationPurpose purpose
) {

    public IssueEmailVerificationCommand toCommand() {
        return new IssueEmailVerificationCommand(email, purpose);
    }
}
