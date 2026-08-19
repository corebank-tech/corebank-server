package com.shinhan.corebank.account.application.port.out;

public interface WithdrawalAccountAuthVerificationPort {

    void verifyAccountPasswordToken(
            String token,
            Long customerId,
            Long accountId
    );

    void verifyOtpToken(
            String token,
            Long customerId,
            Long accountId
    );
}