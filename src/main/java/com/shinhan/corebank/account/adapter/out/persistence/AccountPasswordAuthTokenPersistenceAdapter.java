package com.shinhan.corebank.account.adapter.out.persistence;

import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenStorePort;
import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import com.shinhan.corebank.common.authtoken.AuthTokenPurpose;
import com.shinhan.corebank.common.authtoken.AuthTokenStore;
import com.shinhan.corebank.common.ephemeralstore.ConditionalOnJdbcEphemeralStore;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 계좌비밀번호 인증 토큰을 MySQL에 저장하고 호출자 트랜잭션 안에서 한 번만 소비한다.
@Component
@ConditionalOnJdbcEphemeralStore
@RequiredArgsConstructor
public class AccountPasswordAuthTokenPersistenceAdapter implements AccountPasswordAuthTokenStorePort {

    private static final AuthTokenPurpose PURPOSE = AuthTokenPurpose.ACCOUNT_PASSWORD_AUTH;

    private final AuthTokenStore authTokenStore;

    @Override
    public void save(String token, AccountPasswordAuthTokenPayload payload, Duration ttl) {
        authTokenStore.save(PURPOSE, token, payload.customerId(), payload, ttl);
    }

    @Override
    public boolean consumeIfMatches(String token, AccountPasswordAuthTokenPayload expectedPayload) {
        return authTokenStore.consumeIfMatches(PURPOSE, token, expectedPayload);
    }

    @Override
    public boolean consumeIfCustomerMatches(String token, Long customerId) {
        return authTokenStore.consumeIfCustomerMatches(PURPOSE, token, customerId);
    }
}
