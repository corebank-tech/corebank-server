package com.shinhan.corebank.signup.domain.model;

import java.util.List;

// 고객·약관·계좌를 하나의 트랜잭션으로 등록할 정보를 묶는다.
public record SignupCompletionSnapshot(
        TempSignupTokenPayload signup,
        ExistingBankCustomerProfile customerProfile,
        List<ExistingBankAccountSnapshot> accounts
) {
    public SignupCompletionSnapshot {
        accounts = List.copyOf(accounts);
    }
}
