package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.TermsAgreementResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 약관 동의 인증 토큰과 남은 유효시간을 반환한다.
public record TermsAuthTokenResponse(
        @Schema(
                description = "회원가입 입력 검증에서 한 번 사용할 수 있는 약관 동의 인증 토큰",
                example = "TERMS_AUTH_7xP9qK2RmY5vLw8ZbC6dE4fG1hH0jM3n"
        )
        String termsAuthToken,

        @Schema(description = "약관 동의 인증 토큰 남은 유효시간(초)", example = "1800")
        long expiresIn
) {

    public static TermsAuthTokenResponse from(
            TermsAgreementResult result
    ) {
        return new TermsAuthTokenResponse(
                result.termsAuthToken(),
                result.expiresIn()
        );
    }
}
