package com.shinhan.corebank.signup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.signup.token")
public record SignupTokenProperties(
        Duration termsAuthTtl
) {
}
