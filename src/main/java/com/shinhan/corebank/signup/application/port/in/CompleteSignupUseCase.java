package com.shinhan.corebank.signup.application.port.in;

// 검증된 임시 가입정보를 최종 고객정보로 등록한다.
public interface CompleteSignupUseCase {

    CompleteSignupResult complete(CompleteSignupCommand command);
}
