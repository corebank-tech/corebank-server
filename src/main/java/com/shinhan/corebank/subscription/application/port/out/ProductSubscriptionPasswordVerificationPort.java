package com.shinhan.corebank.subscription.application.port.out;

public interface ProductSubscriptionPasswordVerificationPort {
    void verifyAccountPasswordToken(String token, Long customerId, Long accountId);
}
