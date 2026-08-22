package com.shinhan.corebank.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// 계좌비밀번호 인증 완료 토큰의 유효시간을 설정으로 관리한다.
@ConfigurationProperties(prefix = "app.account.password")
public record AccountPasswordProperties(
        Duration authTokenTtl
) {

    public AccountPasswordProperties {
        if (authTokenTtl == null
                || authTokenTtl.isZero()
                || authTokenTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "계좌비밀번호 인증 토큰 TTL은 양수여야 합니다."
            );
        }
    }
}
