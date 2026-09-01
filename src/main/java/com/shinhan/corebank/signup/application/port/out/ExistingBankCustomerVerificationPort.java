package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.ExistingBankAccountVerification;

// 기존 은행 원장의 고객·계좌 정보를 검증하는 경계를 제공한다.
public interface ExistingBankCustomerVerificationPort {

    ExistingBankAccountVerification verify(
            String userName, String birthDate, String accountNumber, String accountPassword);
}
