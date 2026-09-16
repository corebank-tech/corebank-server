package com.shinhan.corebank.customer.api;

// 회원가입 모듈에 고객 식별정보의 중복 여부만 공개한다.
public interface CustomerRegistrationQuery {

    boolean existsByUserId(String userId);

    boolean existsByEmail(String email);

    boolean existsByExistingBankCustomerId(String existingBankCustomerId);
}
