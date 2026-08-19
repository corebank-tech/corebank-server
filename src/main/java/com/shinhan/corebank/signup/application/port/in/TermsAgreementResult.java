package com.shinhan.corebank.signup.application.port.in;

public record TermsAgreementResult(
        String termsAuthToken,
        long expiresIn
) {
}