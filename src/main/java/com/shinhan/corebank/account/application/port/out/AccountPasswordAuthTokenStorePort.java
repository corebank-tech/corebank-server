package com.shinhan.corebank.account.application.port.out;

import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import java.time.Duration;

// 계좌비밀번호 인증 토큰의 저장과 원자적 소비를 정의한다.
public interface AccountPasswordAuthTokenStorePort {

    void save(String token, AccountPasswordAuthTokenPayload payload, Duration ttl);

    boolean consumeIfMatches(String token, AccountPasswordAuthTokenPayload expectedPayload);

    // 저장된 customerId가 일치할 때만 토큰을 원자적으로 삭제한다.
    boolean consumeIfCustomerMatches(String token, Long customerId);
}
