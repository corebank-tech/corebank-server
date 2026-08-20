package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.TermsAuthTokenPayload;

import java.time.Duration;
import java.util.Optional;

// 약관 동의 인증 토큰의 저장과 일회성 소비 기능을 추상화한다.
public interface TermsAuthTokenPort {

    void save(
            String termsAuthToken,
            TermsAuthTokenPayload payload,
            Duration ttl
    );

    Optional<TermsAuthTokenPayload> consume(
            String termsAuthToken
    );
}
