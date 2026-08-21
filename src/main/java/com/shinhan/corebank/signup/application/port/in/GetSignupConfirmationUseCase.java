package com.shinhan.corebank.signup.application.port.in;

// tempSignupToken으로 회원가입 확인 화면 정보를 조회한다.
public interface GetSignupConfirmationUseCase {

    SignupConfirmationResult getConfirmation(String tempSignupToken);
}
