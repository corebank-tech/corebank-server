package com.shinhan.corebank.otp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// OTP 번호와 인증 완료 토큰의 유효시간을 설정으로 관리한다.
@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
        Duration codeTtl,
        Duration authTokenTtl,
        boolean exposeCode
) {
}
