package com.shinhan.corebank.subscription.application.port.out;

public interface MaturityTerminationPasswordVerificationPort {

    void verifyAndConsumeAccountPasswordToken(String token, Long customerId, Long accountId);
}
