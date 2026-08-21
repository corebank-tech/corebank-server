package com.shinhan.corebank.otp.application.port.out;

import com.shinhan.corebank.otp.domain.model.OtpAuthTokenPayload;

import java.time.Duration;
import java.util.Optional;

// otpAuthToken 페이로드를 Redis에 저장하고 값 일치 조건으로 원자적 소비한다.
public interface OtpAuthTokenStorePort {
    void save(String otpAuthToken, OtpAuthTokenPayload payload, Duration ttl);
    Optional<OtpAuthTokenPayload> find(String otpAuthToken);
    boolean consumeIfMatches(String otpAuthToken, OtpAuthTokenPayload expectedPayload);
}
