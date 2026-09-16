package com.shinhan.corebank.otp.application.port.out;

import java.time.Duration;
import java.util.Optional;

// 고객별 OTP 발급 임계 구역을 여러 애플리케이션 인스턴스에서 직렬화한다.
public interface OtpIssueLockPort {
    Optional<String> tryAcquire(Long customerId, Duration ttl);

    void release(Long customerId, String ownerId);
}
