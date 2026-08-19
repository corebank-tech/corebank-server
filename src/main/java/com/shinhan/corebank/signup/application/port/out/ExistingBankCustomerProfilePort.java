package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.ExistingBankCustomerProfile;

import java.util.Optional;

// 기존 은행 고객 식별자로 확인 화면용 기본정보를 조회한다.
public interface ExistingBankCustomerProfilePort {

    Optional<ExistingBankCustomerProfile> findByCustomerId(String customerId);
}
