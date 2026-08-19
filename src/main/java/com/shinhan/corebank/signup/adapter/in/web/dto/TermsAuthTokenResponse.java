package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.TermsAgreementResult;

// 약관 동의 인증 토큰과 남은 유효시간을 반환한다.
public record TermsAuthTokenResponse(
        String termsAuthToken,
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
