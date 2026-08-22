package com.shinhan.corebank.auth.adapter.in.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

// CSRF 쿠키 설정을 바인딩하고 선택적 공유 도메인을 정규화한다.
@ConfigurationProperties(prefix = "app.security.csrf")
public record CsrfProperties(String cookieDomain) {

    public CsrfProperties {
        cookieDomain = cookieDomain == null || cookieDomain.isBlank()
                ? null
                : cookieDomain.trim();
    }
}
