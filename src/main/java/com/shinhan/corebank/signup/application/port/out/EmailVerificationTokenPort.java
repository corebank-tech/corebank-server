package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.EmailVerificationTokenPayload;

import java.time.Duration;
import java.util.Optional;

// 이메일 인증 완료 토큰의 TTL 저장과 일회성 소비를 추상화한다.
public interface EmailVerificationTokenPort {

    void save(
            String token,
            EmailVerificationTokenPayload payload,
            Duration ttl
    );

    Optional<EmailVerificationTokenPayload> find(String token);

    Optional<EmailVerificationTokenPayload> consume(String token);
}
