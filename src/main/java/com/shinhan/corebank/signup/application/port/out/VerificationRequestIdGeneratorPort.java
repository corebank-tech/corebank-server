package com.shinhan.corebank.signup.application.port.out;

// 이메일 인증 요청을 식별할 예측 불가능한 ID를 생성한다.
public interface VerificationRequestIdGeneratorPort {

    String generateEmailVerificationId();
}
