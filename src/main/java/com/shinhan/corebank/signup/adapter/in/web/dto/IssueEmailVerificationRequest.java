package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationCommand;
import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 인증번호를 발급할 이메일과 인증 목적을 전달한다.
public record IssueEmailVerificationRequest(
        @NotBlank
        @Email
        @Size(max = 100)
        String email,

        @NotNull
        EmailVerificationPurpose purpose
) {

    public IssueEmailVerificationCommand toCommand() {
        return new IssueEmailVerificationCommand(email, purpose);
    }
}
