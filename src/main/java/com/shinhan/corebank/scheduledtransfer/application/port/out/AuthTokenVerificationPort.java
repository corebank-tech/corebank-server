package com.shinhan.corebank.scheduledtransfer.application.port.out;

public interface AuthTokenVerificationPort {
    void verifyAndConsume(String authToken, Long customerId, Long accountId);
}
