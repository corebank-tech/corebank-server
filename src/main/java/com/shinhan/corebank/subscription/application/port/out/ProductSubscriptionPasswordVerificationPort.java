package com.shinhan.corebank.subscription.application.port.out;

public interface ProductSubscriptionPasswordVerificationPort {
    void verifyAndConsumeAccountPasswordToken(String token, Long customerId, Long accountId);
}
