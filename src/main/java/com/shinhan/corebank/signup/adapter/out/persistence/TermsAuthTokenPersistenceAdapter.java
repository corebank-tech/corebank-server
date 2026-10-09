package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.common.authtoken.AuthTokenPurpose;
import com.shinhan.corebank.common.authtoken.AuthTokenStore;
import com.shinhan.corebank.common.ephemeralstore.ConditionalOnJdbcEphemeralStore;
import com.shinhan.corebank.signup.application.port.out.TermsAuthTokenPort;
import com.shinhan.corebank.signup.domain.model.TermsAuthTokenPayload;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 약관 동의 인증 토큰을 MySQL에 저장하고 한 번만 소비한다.
@Component
@ConditionalOnJdbcEphemeralStore
@RequiredArgsConstructor
public class TermsAuthTokenPersistenceAdapter implements TermsAuthTokenPort {

    private static final AuthTokenPurpose PURPOSE = AuthTokenPurpose.TERMS_AUTH;

    private final AuthTokenStore authTokenStore;

    @Override
    public void save(String token, TermsAuthTokenPayload payload, Duration ttl) {
        authTokenStore.save(PURPOSE, token, null, payload, ttl);
    }

    @Override
    public Optional<TermsAuthTokenPayload> find(String token) {
        return authTokenStore.find(PURPOSE, token, TermsAuthTokenPayload.class);
    }

    @Override
    public Optional<TermsAuthTokenPayload> consume(String token) {
        return authTokenStore.consume(PURPOSE, token, TermsAuthTokenPayload.class);
    }
}
