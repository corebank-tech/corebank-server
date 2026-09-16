package com.shinhan.corebank.signup.application.port.in;

// Phase 1 인증 요청 ID와 화면 확인용 인증번호를 반환한다.
public record IssueEmailVerificationResult(String emailVerificationId, String verificationCode, long expiresIn) {}
