package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.common.authtoken.AuthTokenPurpose;
import com.shinhan.corebank.common.authtoken.AuthTokenStore;
import com.shinhan.corebank.common.ephemeralstore.ConditionalOnJdbcEphemeralStore;
import com.shinhan.corebank.signup.application.port.out.EmailVerificationTokenPort;
import com.shinhan.corebank.signup.domain.model.EmailVerificationTokenPayload;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 이메일 인증 완료 토큰을 MySQL에 저장하고 한 번만 소비한다.
@Component
@ConditionalOnJdbcEphemeralStore
@RequiredArgsConstructor
public class EmailVerificationTokenPersistenceAdapter implements EmailVerificationTokenPort {

    private static final AuthTokenPurpose PURPOSE = AuthTokenPurpose.EMAIL_VERIFICATION;

    private final AuthTokenStore authTokenStore;

    @Override
    public void save(String token, EmailVerificationTokenPayload payload, Duration ttl) {
        authTokenStore.save(PURPOSE, token, null, payload, ttl);
    }

    @Override
    public Optional<EmailVerificationTokenPayload> find(String token) {
        return authTokenStore.find(PURPOSE, token, EmailVerificationTokenPayload.class);
    }

    @Override
    public Optional<EmailVerificationTokenPayload> consume(String token) {
        return authTokenStore.consume(PURPOSE, token, EmailVerificationTokenPayload.class);
    }
}
