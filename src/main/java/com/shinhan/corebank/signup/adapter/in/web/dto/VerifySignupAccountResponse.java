package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 회원가입 계좌 인증 성공 토큰을 HTTP 응답으로 반환한다.
public record VerifySignupAccountResponse(
        @Schema(
                        description = "회원가입 입력 검증에서 한 번 사용할 수 있는 계좌 인증 토큰",
                        example = "ACCOUNT_AUTH_7xP9qK2RmY5vLw8ZbC6dE4fG1hH0jM3n")
                String accountAuthToken,
        @Schema(description = "계좌 인증 토큰 남은 유효시간(초)", example = "1800") long expiresIn) {

    public static VerifySignupAccountResponse from(VerifySignupAccountResult result) {
        return new VerifySignupAccountResponse(result.accountAuthToken(), result.expiresIn());
    }
}
