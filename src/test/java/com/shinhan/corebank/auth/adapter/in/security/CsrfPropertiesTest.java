package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CsrfPropertiesTest {

    @Test
    @DisplayName("CSRF 쿠키 공유 도메인의 앞뒤 공백을 제거한다")
    void trimsCookieDomain() {
        CsrfProperties properties = new CsrfProperties(
                " corebank.cloud "
        );

        assertThat(properties.cookieDomain()).isEqualTo("corebank.cloud");
    }

    @Test
    @DisplayName("CSRF 쿠키 공유 도메인이 없거나 공백이면 host-only 설정으로 정규화한다")
    void normalizesMissingCookieDomainToNull() {
        assertThat(new CsrfProperties(null).cookieDomain()).isNull();
        assertThat(new CsrfProperties(" ").cookieDomain()).isNull();
    }
}
