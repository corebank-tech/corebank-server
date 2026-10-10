package com.shinhan.corebank.otp.adapter.out.persistence;

import com.shinhan.corebank.common.authtoken.AuthTokenPurpose;
import com.shinhan.corebank.common.authtoken.AuthTokenStore;
import com.shinhan.corebank.common.ephemeralstore.ConditionalOnJdbcEphemeralStore;
import com.shinhan.corebank.otp.application.port.out.OtpAuthTokenStorePort;
import com.shinhan.corebank.otp.domain.model.OtpAuthTokenPayload;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// otpAuthToken을 MySQL에 저장하고 payload가 같을 때만 호출자 트랜잭션 안에서 소비한다.
@Component
@ConditionalOnJdbcEphemeralStore
@RequiredArgsConstructor
public class OtpAuthTokenPersistenceAdapter implements OtpAuthTokenStorePort {

    private static final AuthTokenPurpose PURPOSE = AuthTokenPurpose.OTP_AUTH;

    private final AuthTokenStore authTokenStore;

    @Override
    public void save(String otpAuthToken, OtpAuthTokenPayload payload, Duration ttl) {
        authTokenStore.save(PURPOSE, otpAuthToken, payload.customerId(), payload, ttl);
    }

    @Override
    public Optional<OtpAuthTokenPayload> find(String otpAuthToken) {
        return authTokenStore.find(PURPOSE, otpAuthToken, OtpAuthTokenPayload.class);
    }

    @Override
    public boolean consumeIfMatches(String otpAuthToken, OtpAuthTokenPayload expectedPayload) {
        return authTokenStore.consumeIfMatches(PURPOSE, otpAuthToken, expectedPayload);
    }
}
