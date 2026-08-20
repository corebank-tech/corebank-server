package com.shinhan.corebank.signup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// 이메일 인증번호의 짧은 유효시간을 외부 설정으로 관리한다.
@ConfigurationProperties(prefix = "app.signup.email-verification")
public record EmailVerificationProperties(
        Duration codeTtl
) {
}
