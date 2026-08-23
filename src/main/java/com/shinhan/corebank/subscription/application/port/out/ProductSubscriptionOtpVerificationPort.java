package com.shinhan.corebank.subscription.application.port.out;

public interface ProductSubscriptionOtpVerificationPort {

    void verifyAndConsume(
            String otpAuthToken,
            Long customerId,
            Long productId,
            Long subscriptionAmount,
            Integer termMonths,
            Long withdrawalAccountId
    );
}
