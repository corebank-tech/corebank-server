package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.ValidateSignupCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 회원가입 3단계 입력 검증 HTTP 요청을 표현한다.
public record ValidateSignupRequest(
        @Schema(description = "약관 동의 검증 후 발급된 1회성 인증 토큰", example = "TERMS_AUTH_7xP9qK2RmY5vLw8Z", nullable = true)
                String termsAuthToken,
        @Schema(description = "실명·계좌 인증 후 발급된 1회성 인증 토큰", example = "ACCOUNT_AUTH_7xP9qK2RmY5vLw8Z", nullable = true)
                String accountAuthToken,
        @Schema(description = "아이디 중복확인 후 발급된 1회성 토큰", example = "USER_ID_CHECK_7xP9qK2RmY5vLw8Z", nullable = true)
                String userIdCheckToken,
        @Schema(description = "이메일 인증 후 발급된 1회성 인증 토큰", example = "EMAIL_AUTH_7xP9qK2RmY5vLw8Z", nullable = true)
                String emailVerificationToken,
        @Schema(
                        description = "정보수정 요청에서 사용할 기존 임시 가입 토큰. 최초 요청에서는 null",
                        example = "TEMP_SIGNUP_7xP9qK2RmY5vLw8Z",
                        nullable = true)
                String tempSignupToken,
        @Schema(
                        description = "로그인 아이디. 영문 소문자로 시작하는 영문 소문자·숫자 6~16자리",
                        example = "corebank01",
                        pattern = "^[a-z][a-z0-9]{5,15}$")
                @NotBlank
                @Pattern(regexp = "^[a-z][a-z0-9]{5,15}$")
                String userId,
        @Schema(
                        description = "로그인 비밀번호",
                        example = "Corebank!1234",
                        format = "password",
                        accessMode = Schema.AccessMode.WRITE_ONLY)
                @NotBlank
                String userPassword,
        @Schema(
                        description = "로그인 비밀번호 확인값",
                        example = "Corebank!1234",
                        format = "password",
                        accessMode = Schema.AccessMode.WRITE_ONLY)
                @NotBlank
                String userPasswordConfirm,
        @Schema(description = "가입 이메일", example = "user@mail.com", maxLength = 100) @NotBlank @Email @Size(max = 100)
                String email,
        @Schema(description = "휴대폰 번호. 하이픈 없는 숫자 11자리", example = "01012345678", pattern = "^\\d{11}$")
                @NotBlank
                @Pattern(regexp = "^\\d{11}$")
                String phoneNumber) {

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
                phoneNumber);
    }
}
