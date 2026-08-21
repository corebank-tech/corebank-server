package com.shinhan.corebank.signup.application.port.in;

// 회원가입 실명·계좌 인증 유스케이스를 정의한다.
public interface VerifySignupAccountUseCase {

    VerifySignupAccountResult verify(VerifySignupAccountCommand command);
}
