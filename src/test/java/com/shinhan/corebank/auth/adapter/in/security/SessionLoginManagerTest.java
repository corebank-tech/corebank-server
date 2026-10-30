package com.shinhan.corebank.auth.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.application.port.in.LoginResult;
import com.shinhan.corebank.customer.api.AdminPermission;
import com.shinhan.corebank.customer.api.AdminPermissionFacade;
import com.shinhan.corebank.customer.api.CustomerRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

class SessionLoginManagerTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-16T01:00:00Z"), KST);

    private final HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    // 운영 설정과 같은 30분을 쓴다.
    private static final AdminSessionProperties ADMIN_SESSION = new AdminSessionProperties(Duration.ofMinutes(30));

    // 고객 만료시간의 정본인 server.servlet.session.timeout 이다. 운영 설정과 같은 10분을 쓴다.
    private static final ServerProperties SERVER = serverPropertiesWith(Duration.ofMinutes(10));

    private final AdminPermissionFacade adminPermissionFacade = mock(AdminPermissionFacade.class);

    private final SessionLoginManager manager = new SessionLoginManager(
            new SecurityConfig().sessionAuthenticationStrategy(),
            repository,
            adminPermissionFacade,
            ADMIN_SESSION,
            SERVER,
            FIXED_CLOCK);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 고객과 ROLE_CUSTOMER를 SecurityContext에 저장한다")
    void storesAuthenticatedCustomerInSession() {
        MockHttpServletRequest request = requestWithSession(600);
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.establishSession(new LoginResult(1L, "login-user", "홍길동", CustomerRole.CUSTOMER), request, response);

        SecurityContext context = storedContext(request.getSession(false));
        Authentication authentication = context.getAuthentication();

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedCustomer(1L, "login-user", "홍길동"));
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_CUSTOMER");
        assertThat(authentication.getCredentials()).isNull();
        assertThat(SecurityContextHolder.getContext()).isSameAs(context);
        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(600);
        verifyNoInteractions(adminPermissionFacade);
    }

    @Test
    @DisplayName("기존 세션 ID를 변경한 뒤 인증정보를 저장한다")
    void changesExistingSessionId() {
        MockHttpServletRequest request = requestWithSession(600);
        MockHttpServletResponse response = new MockHttpServletResponse();
        String previousSessionId = request.getSession(false).getId();

        manager.establishSession(new LoginResult(1L, "login-user", "홍길동", CustomerRole.CUSTOMER), request, response);

        assertThat(request.getSession(false).getId()).isNotEqualTo(previousSessionId);
    }

    @Test
    @DisplayName("고객 세션은 전역 timeout 으로 만료시각을 계산한다")
    void calculatesExpirationFromGlobalTimeout() {
        MockHttpServletRequest request = requestWithSession(60);

        OffsetDateTime sessionExpiresAt = manager.establishSession(
                new LoginResult(1L, "login-user", "홍길동", CustomerRole.CUSTOMER),
                request,
                new MockHttpServletResponse());

        assertThat(sessionExpiresAt).isEqualTo(OffsetDateTime.parse("2026-08-16T10:10:00+09:00"));
    }

    // 전역 timeout 은 고객 기준 10분이고, 관리자는 전용 설정인 30분을 받는다.
    @Test
    @DisplayName("관리자 세션은 전역 timeout 을 30분으로 덮고 만료시각도 30분 뒤를 준다")
    void overridesSessionTimeoutForAdmin() {
        MockHttpServletRequest request = requestWithSession(600);

        OffsetDateTime sessionExpiresAt = manager.establishSession(
                new LoginResult(1L, "admin-user", "관리자", CustomerRole.ADMIN), request, new MockHttpServletResponse());

        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(1800);
        assertThat(sessionExpiresAt).isEqualTo(OffsetDateTime.parse("2026-08-16T10:30:00+09:00"));
    }

    // 인가는 이 authority 만 보므로, 권한 행이 여기 실리지 않으면 관리자 API 가 전부 막힌다.
    @Test
    @DisplayName("관리자는 ROLE_ADMIN 과 부여된 권한을 authority 로 받는다")
    void grantsAdminAuthorityWithPermissions() {
        MockHttpServletRequest request = requestWithSession(600);
        when(adminPermissionFacade.findPermissions(1L))
                .thenReturn(List.of(AdminPermission.GL_READ, AdminPermission.CUSTOMER_READ));

        manager.establishSession(
                new LoginResult(1L, "admin-user", "관리자", CustomerRole.ADMIN), request, new MockHttpServletResponse());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "GL_READ", "CUSTOMER_READ");
    }

    @Test
    @DisplayName("인증정보 저장 후 세션이 없으면 설정 오류로 처리한다")
    void rejectsMissingSessionAfterAuthentication() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        SessionLoginManager invalidManager = new SessionLoginManager(
                mock(SessionAuthenticationStrategy.class),
                mock(SecurityContextRepository.class),
                adminPermissionFacade,
                ADMIN_SESSION,
                SERVER,
                FIXED_CLOCK);
        when(request.getSession(false)).thenReturn(null);

        assertThatThrownBy(() -> invalidManager.establishSession(
                        new LoginResult(1L, "login-user", "홍길동", CustomerRole.CUSTOMER), request, response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("로그인 세션이 생성되지 않았습니다.");
    }

    // 전역 설정이 0 이면 만료되지 않는 세션이 되므로, 세션을 만들기 전에 막아야 한다.
    @Test
    @DisplayName("전역 timeout 이 0 이하면 세션을 만들지 않고 설정 오류로 처리한다")
    void rejectsNonPositiveGlobalTimeout() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        SessionLoginManager invalidManager = new SessionLoginManager(
                mock(SessionAuthenticationStrategy.class),
                mock(SecurityContextRepository.class),
                adminPermissionFacade,
                ADMIN_SESSION,
                serverPropertiesWith(Duration.ZERO),
                FIXED_CLOCK);

        assertThatThrownBy(() -> invalidManager.establishSession(
                        new LoginResult(1L, "login-user", "홍길동", CustomerRole.CUSTOMER),
                        request,
                        new MockHttpServletResponse()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("세션 만료시간은 1초 이상이어야 합니다.");
        assertThat(request.getSession(false)).isNull();
    }

    // 리뷰 지적(PR #639). ChangeSessionIdAuthenticationStrategy 는 세션 ID만 바꾸므로, 관리자 세션에서
    // 로그아웃 없이 고객으로 다시 로그인하면 관리자 만료시간(30분)이 고객 세션에 남을 수 있다.
    @Test
    @DisplayName("관리자 세션에서 고객으로 다시 로그인하면 만료시간이 고객 기준으로 돌아온다")
    void restoresCustomerTimeoutOnReloginAsCustomer() {
        MockHttpServletRequest request = requestWithSession(60);

        manager.establishSession(
                new LoginResult(1L, "admin-user", "관리자", CustomerRole.ADMIN), request, new MockHttpServletResponse());

        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(1800);

        OffsetDateTime sessionExpiresAt = manager.establishSession(
                new LoginResult(2L, "login-user", "홍길동", CustomerRole.CUSTOMER),
                request,
                new MockHttpServletResponse());

        assertThat(request.getSession(false).getMaxInactiveInterval()).isEqualTo(600);
        assertThat(sessionExpiresAt).isEqualTo(OffsetDateTime.parse("2026-08-16T10:10:00+09:00"));
    }

    private static ServerProperties serverPropertiesWith(Duration sessionTimeout) {
        ServerProperties serverProperties = new ServerProperties();
        serverProperties.getServlet().getSession().setTimeout(sessionTimeout);
        return serverProperties;
    }

    private MockHttpServletRequest requestWithSession(int timeoutSeconds) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setMaxInactiveInterval(timeoutSeconds);
        return request;
    }

    private SecurityContext storedContext(HttpSession session) {
        return (SecurityContext) session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    }
}
