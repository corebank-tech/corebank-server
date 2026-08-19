package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.TermsAuthTokenPayload;

import java.time.Duration;
import java.util.Optional;

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