package com.shinhan.corebank.transfer.application.port.out;

public interface TransferOtpVerificationPort {

    void verifyAndConsume(
            String otpAuthToken, Long customerId, Long withdrawalAccountId, String depositAccountNumber, long amount);
}
