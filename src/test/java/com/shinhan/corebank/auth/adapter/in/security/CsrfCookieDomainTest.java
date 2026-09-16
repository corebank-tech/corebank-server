package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;

class CsrfCookieDomainTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    @DisplayName("운영 CSRF 쿠키는 corebank.cloud 서브도메인에서 공유된다")
    void sharesCsrfCookieAcrossProductionSubdomains() {
        CookieCsrfTokenRepository repository =
                securityConfig.csrfTokenRepository(true, new CsrfProperties("corebank.cloud"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);

        repository.saveToken(token, request, response);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).singleElement().satisfies(cookie -> assertThat(cookie)
                .contains("XSRF-TOKEN=")
                .contains("Path=/")
                .contains("Domain=corebank.cloud")
                .contains("Secure")
                .doesNotContain("HttpOnly"));
    }

    @Test
    @DisplayName("쿠키 도메인이 비어 있으면 로컬 CSRF 쿠키는 host-only로 발급된다")
    void keepsLocalCsrfCookieHostOnly() {
        CookieCsrfTokenRepository repository = securityConfig.csrfTokenRepository(false, new CsrfProperties(" "));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(repository.generateToken(request), request, response);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).singleElement().satisfies(cookie -> assertThat(cookie)
                .contains("XSRF-TOKEN=")
                .contains("Path=/")
                .doesNotContain("Domain=")
                .doesNotContain("Secure")
                .doesNotContain("HttpOnly"));
    }

    @Test
    @DisplayName("운영 CSRF 쿠키 삭제에도 발급 때와 같은 공유 도메인을 사용한다")
    void deletesProductionCsrfCookieFromSharedDomain() {
        CookieCsrfTokenRepository repository =
                securityConfig.csrfTokenRepository(true, new CsrfProperties("corebank.cloud"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(null, new MockHttpServletRequest(), response);

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).singleElement().satisfies(cookie -> assertThat(cookie)
                .contains("XSRF-TOKEN=")
                .contains("Max-Age=0")
                .contains("Domain=corebank.cloud")
                .contains("Path=/"));
    }
}
