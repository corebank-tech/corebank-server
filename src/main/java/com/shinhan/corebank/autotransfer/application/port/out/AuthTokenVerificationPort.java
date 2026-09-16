package com.shinhan.corebank.autotransfer.application.port.out;

public interface AuthTokenVerificationPort {
    void verifyAndConsume(String authToken, Long customerId, Long accountId);
}
