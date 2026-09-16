package com.shinhan.corebank.signup.application.port.in;

// 회원가입 계좌 인증 성공 토큰과 유효시간을 반환한다.
public record VerifySignupAccountResult(String accountAuthToken, long expiresIn) {}
