package com.shinhan.corebank.signup.domain.model;

public record SignupTerm(
        String termsId,
        String termsCode,
        String version,
        String title,
        String content,
        boolean required,
        boolean viewRequired
) {
}
