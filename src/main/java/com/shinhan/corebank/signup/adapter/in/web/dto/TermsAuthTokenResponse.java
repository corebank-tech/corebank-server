package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.TermsAgreementResult;

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
