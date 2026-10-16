package com.shinhan.corebank.signup.adapter.out.persistence;

import com.shinhan.corebank.common.authtoken.AuthTokenPurpose;
import com.shinhan.corebank.common.authtoken.AuthTokenStore;
import com.shinhan.corebank.common.ephemeralstore.ConditionalOnJdbcEphemeralStore;
import com.shinhan.corebank.signup.application.port.out.UserIdCheckTokenPort;
import com.shinhan.corebank.signup.domain.model.UserIdCheckTokenPayload;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 아이디 중복확인 토큰을 MySQL에 저장하고 한 번만 소비한다.
@Component
@ConditionalOnJdbcEphemeralStore
@RequiredArgsConstructor
public class UserIdCheckTokenPersistenceAdapter implements UserIdCheckTokenPort {

    private static final AuthTokenPurpose PURPOSE = AuthTokenPurpose.USER_ID_CHECK;

    private final AuthTokenStore authTokenStore;

    @Override
    public void save(String token, UserIdCheckTokenPayload payload, Duration ttl) {
        authTokenStore.save(PURPOSE, token, null, payload, ttl);
    }

    @Override
    public Optional<UserIdCheckTokenPayload> find(String token) {
        return authTokenStore.find(PURPOSE, token, UserIdCheckTokenPayload.class);
    }

    @Override
    public Optional<UserIdCheckTokenPayload> consume(String token) {
        return authTokenStore.consume(PURPOSE, token, UserIdCheckTokenPayload.class);
    }
}
