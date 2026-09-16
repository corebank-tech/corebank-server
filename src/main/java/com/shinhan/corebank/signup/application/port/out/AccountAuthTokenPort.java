package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.AccountAuthTokenPayload;
import java.time.Duration;
import java.util.Optional;

// accountAuthToken의 Redis 저장과 일회성 소비 기능을 제공한다.
public interface AccountAuthTokenPort {

    void save(String token, AccountAuthTokenPayload payload, Duration ttl);

    Optional<AccountAuthTokenPayload> find(String token);

    Optional<AccountAuthTokenPayload> consume(String token);
}
