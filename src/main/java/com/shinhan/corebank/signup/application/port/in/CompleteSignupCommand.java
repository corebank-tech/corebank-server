package com.shinhan.corebank.signup.application.port.in;

// 회원가입 완료에 사용할 일회성 임시 가입 토큰을 전달한다.
public record CompleteSignupCommand(String tempSignupToken) {}
