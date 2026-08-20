package com.shinhan.corebank.signup.application.port.in;

// 회원가입 아이디의 형식과 중복 여부를 검증한다.
public interface CheckUserIdUseCase {

    CheckUserIdResult check(CheckUserIdCommand command);
}
