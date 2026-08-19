package com.shinhan.corebank.signup.application.port.out;

// Phase 1에서 사용할 숫자 6자리 이메일 인증번호를 생성한다.
public interface EmailVerificationCodeGeneratorPort {

    String generate();
}
