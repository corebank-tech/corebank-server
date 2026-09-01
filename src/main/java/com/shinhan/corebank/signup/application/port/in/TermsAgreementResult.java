package com.shinhan.corebank.signup.application.port.in;

// 약관 동의 검증 결과로 인증 토큰과 유효시간을 전달한다.
public record TermsAgreementResult(String termsAuthToken, long expiresIn) {}
