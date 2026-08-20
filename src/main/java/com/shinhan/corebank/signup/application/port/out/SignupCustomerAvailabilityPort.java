package com.shinhan.corebank.signup.application.port.out;

// 회원가입에 필요한 고객 아이디와 이메일 중복조회 계약이다.
public interface SignupCustomerAvailabilityPort {

    boolean isUserIdTaken(String userId);

    boolean isEmailTaken(String email);
}
