package com.shinhan.corebank.account.application.port.out;

public interface WithdrawalAccountPasswordVerificationPort {

    void verifyAccountPasswordToken(
            String token,
            Long customerId,
            Long accountId
    );
}
