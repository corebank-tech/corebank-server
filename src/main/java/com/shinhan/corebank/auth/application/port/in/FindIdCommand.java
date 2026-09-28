package com.shinhan.corebank.auth.application.port.in;

// 아이디 찾기에 필요한 고객 및 계좌 입력값을 전달한다.
public record FindIdCommand(String customerName, String birthDate, String accountNumber, String accountPassword) {}
