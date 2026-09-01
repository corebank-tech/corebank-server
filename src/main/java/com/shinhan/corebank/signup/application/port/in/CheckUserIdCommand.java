package com.shinhan.corebank.signup.application.port.in;

// 아이디 중복확인 요청값을 application 계층으로 전달한다.
public record CheckUserIdCommand(String userId) {}
