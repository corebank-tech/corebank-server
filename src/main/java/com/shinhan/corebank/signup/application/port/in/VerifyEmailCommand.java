package com.shinhan.corebank.signup.application.port.in;

// 인증 요청 ID와 사용자가 입력한 6자리 코드를 전달한다.
public record VerifyEmailCommand(String emailVerificationId, String verificationCode) {}
