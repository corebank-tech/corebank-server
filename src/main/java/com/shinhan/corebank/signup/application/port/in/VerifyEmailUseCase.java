package com.shinhan.corebank.signup.application.port.in;

// 이메일 인증번호를 검증하고 인증 완료 토큰을 발급한다.
public interface VerifyEmailUseCase {

    VerifyEmailResult verify(VerifyEmailCommand command);
}
