package com.shinhan.corebank.account.application.port.out;

public interface WithdrawalAccountOtpVerificationPort {

    void verifyAndConsume(
            String otpAuthToken,
            Long customerId,
            Long accountId
    );
}
