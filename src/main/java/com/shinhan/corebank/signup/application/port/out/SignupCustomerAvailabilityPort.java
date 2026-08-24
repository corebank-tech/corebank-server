package com.shinhan.corebank.signup.application.port.out;

// 회원가입에 필요한 고객 아이디·이메일·원장 고객 중복조회 계약이다.
public interface SignupCustomerAvailabilityPort {

    boolean isUserIdTaken(String userId);

    boolean isEmailTaken(String email);

    // 같은 원장 고객이 이미 인터넷뱅킹에 가입했는지 조회한다.
    boolean isExistingBankCustomerRegistered(String existingBankCustomerId);
}
