package com.shinhan.corebank.signup.application.port.in;

// 회원가입 입력값과 단계별 인증 완료 사실을 검증한다.
public interface ValidateSignupUseCase {

    ValidateSignupResult validate(ValidateSignupCommand command);
}
