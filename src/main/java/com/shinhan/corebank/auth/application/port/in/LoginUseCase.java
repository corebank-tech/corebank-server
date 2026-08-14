package com.shinhan.corebank.auth.application.port.in;

// 고객 로그인을 검증하고 인증 결과를 반환하는 입력 Port
public interface LoginUseCase {

    LoginResult login(LoginCommand command);
}
