package com.shinhan.corebank.auth.adapter.in.security;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.application.port.in.LoginResult;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SessionLoginManagerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-16T01:00:00Z"),
            ZoneOffset.UTC
    );

    private final HttpSessionSecurityContextRepository repository =
            new HttpSessionSecurityContextRepository();

    private final SessionLoginManager manager = new SessionLoginManager(
            new ChangeSessionIdAuthenticationStrategy(),
            repository,
            FIXED_CLOCK
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 고객과 ROLE_CUSTOMER를 SecurityContext에 저장한다")
    void storesAuthenticatedCustomerInSession() {
        MockHttpServletRequest request = requestWithSession(600);
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.establishSession(
                new LoginResult(1L, "login-user", "홍길동"),
                request,
                response
        );

        SecurityContext context = storedContext(request.getSession(false));
        Authentication authentication = context.getAuthentication();

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(
                new AuthenticatedCustomer(1L, "login-user", "홍길동")
        );
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CUSTOMER");
        assertThat(authentication.getCredentials()).isNull();
        assertThat(SecurityContextHolder.getContext())
                .isSameAs(context);
    }

    @Test
    @DisplayName("기존 세션 ID를 변경한 뒤 인증정보를 저장한다")
    void changesExistingSessionId() {
        MockHttpServletRequest request = requestWithSession(600);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String previousSessionId = request.getSession(false).getId();

        manager.establishSession(
                new LoginResult(1L, "login-user", "홍길동"),
                request,
                response
        );

        assertThat(request.getSession(false).getId())
                .isNotEqualTo(previousSessionId);
    }

    @Test
    @DisplayName("실제 세션 timeout으로 세션 만료시각을 계산한다")
    void calculatesExpirationFromSessionTimeout() {
        MockHttpServletRequest request = requestWithSession(600);

        OffsetDateTime sessionExpiresAt = manager.establishSession(
                new LoginResult(1L, "login-user", "홍길동"),
                request,
                new MockHttpServletResponse()
        );

        assertThat(sessionExpiresAt)
                .isEqualTo(OffsetDateTime.parse("2026-08-16T01:10:00Z"));
    }

    private MockHttpServletRequest requestWithSession(int timeoutSeconds) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setMaxInactiveInterval(timeoutSeconds);
        return request;
    }

    private SecurityContext storedContext(HttpSession session) {
        return (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY
        );
    }
}
