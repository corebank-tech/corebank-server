package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.TempSignupTokenPayload;
import java.time.Duration;
import java.util.Optional;

// tempSignupToken의 저장·조회·일회성 소비 기능을 정의한다.
public interface TempSignupTokenPort {

    void save(String token, TempSignupTokenPayload payload, Duration ttl);

    Optional<TempSignupTokenPayload> find(String token);

    Optional<TempSignupTokenPayload> consume(String token);
}
