package com.shinhan.corebank.autotransfer.application.port.out;

public interface AuthTokenVerificationPort {
    void verify(String authToken, Long accountId, String purpose);
}
