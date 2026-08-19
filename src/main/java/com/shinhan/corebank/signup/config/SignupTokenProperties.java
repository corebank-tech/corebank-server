package com.shinhan.corebank.signup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// 회원가입 인증 토큰별 유효시간 설정을 바인딩한다.
@ConfigurationProperties(prefix = "app.signup.token")
public record SignupTokenProperties(
        Duration termsAuthTtl,
        Duration userIdCheckTtl,
        Duration emailVerificationTtl
) {
}
