package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.common.authtoken.AuthTokenPurpose;
import com.shinhan.corebank.common.authtoken.AuthTokenStore;
import com.shinhan.corebank.common.ephemeralstore.ConditionalOnJdbcEphemeralStore;
import com.shinhan.corebank.signup.application.port.out.TempSignupTokenPort;
import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 임시 회원가입 토큰을 MySQL에 저장하고 한 번만 소비한다.
@Component
@ConditionalOnJdbcEphemeralStore
@RequiredArgsConstructor
public class TempSignupTokenPersistenceAdapter implements TempSignupTokenPort {

    private static final AuthTokenPurpose PURPOSE = AuthTokenPurpose.TEMP_SIGNUP;

    private final AuthTokenStore authTokenStore;

    @Override
    public void save(String token, TempSignupTokenPayload payload, Duration ttl) {
        authTokenStore.save(PURPOSE, token, null, payload, ttl);
    }

    @Override
    public Optional<TempSignupTokenPayload> find(String token) {
        return authTokenStore.find(PURPOSE, token, TempSignupTokenPayload.class);
    }

    @Override
    public Optional<TempSignupTokenPayload> consume(String token) {
        return authTokenStore.consume(PURPOSE, token, TempSignupTokenPayload.class);
    }
}
