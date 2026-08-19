package com.shinhan.corebank.scheduledtransfer.application.port.out;

public interface AuthTokenVerificationPort {
    void verify(String authToken, Long accountId, String purpose);
}
