package com.shinhan.corebank.signup.domain.model;

// 회원가입 확인 화면에 표시할 마스킹된 고객정보를 표현한다.
public record SignupConfirmation(String userName, String userId, String birthDate, String phoneNumber, String email) {}
