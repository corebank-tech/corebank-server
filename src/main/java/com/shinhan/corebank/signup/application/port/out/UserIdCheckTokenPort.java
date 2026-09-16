package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.UserIdCheckTokenPayload;
import java.time.Duration;
import java.util.Optional;

// 아이디 중복확인 토큰의 TTL 저장과 일회성 소비를 추상화한다.
public interface UserIdCheckTokenPort {

    void save(String token, UserIdCheckTokenPayload payload, Duration ttl);

    Optional<UserIdCheckTokenPayload> find(String token);

    Optional<UserIdCheckTokenPayload> consume(String token);
}
