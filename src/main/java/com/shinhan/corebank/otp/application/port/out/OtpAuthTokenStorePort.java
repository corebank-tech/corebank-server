package com.shinhan.corebank.otp.application.port.out;

import java.time.Duration;
import java.util.Optional;

// otpAuthToken과 OTP 요청 ID를 Redis에 저장하고 조건부로 소비한다.
public interface OtpAuthTokenStorePort {
    void save(String otpAuthToken, String otpRequestId, Duration ttl);
    Optional<String> findRequestId(String otpAuthToken);
    boolean consumeIfMatches(String otpAuthToken, String expectedOtpRequestId);
}
