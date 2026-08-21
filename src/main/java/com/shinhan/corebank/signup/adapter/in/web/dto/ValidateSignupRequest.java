package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.ValidateSignupCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 회원가입 3단계 입력 검증 HTTP 요청을 표현한다.
public record ValidateSignupRequest(
        String termsAuthToken,
        String accountAuthToken,
        String userIdCheckToken,
        String emailVerificationToken,
        String tempSignupToken,

        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9]{5,15}$")
        String userId,

        @NotBlank
        String userPassword,

        @NotBlank
        String userPasswordConfirm,

        @NotBlank
        @Email
        @Size(max = 100)
        String email,

        @NotBlank
        @Pattern(regexp = "^\\d{11}$")
        String phoneNumber
) {

    public ValidateSignupCommand toCommand() {
        return new ValidateSignupCommand(
                termsAuthToken,
                accountAuthToken,
                userIdCheckToken,
                emailVerificationToken,
                tempSignupToken,
                userId,
                userPassword,
                userPasswordConfirm,
                email,
                phoneNumber
        );
    }
}
