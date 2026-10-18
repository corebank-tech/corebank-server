package com.shinhan.corebank.auth.adapter.in.security;

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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

// 로그인 성공 고객을 Spring Security 인증 세션으로 저장. 관리자는 권한까지 authority 로 싣는다
@Component
@RequiredArgsConstructor
public class SessionLoginManager {

    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AdminPermissionFacade adminPermissionFacade;
    private final AdminSessionProperties adminSessionProperties;
    private final ServerProperties serverProperties;
    private final Clock clock;

    public OffsetDateTime establishSession(
            LoginResult loginResult, HttpServletRequest request, HttpServletResponse response) {
        Objects.requireNonNull(loginResult, "loginResult must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");

        // 만료시간을 세션보다 먼저 정한다. 설정이 잘못되면 인증 세션을 만들기 전에 멈춰야,
        // 만료되지 않는 세션이 남은 채로 로그인만 실패하는 상태가 생기지 않는다.
        Duration sessionTimeout = sessionTimeoutOf(loginResult.role());

        if (sessionTimeout == null || sessionTimeout.toSeconds() < 1) {
            throw new IllegalStateException("세션 만료시간은 1초 이상이어야 합니다.");
        }

        AuthenticatedCustomer principal =
                new AuthenticatedCustomer(loginResult.customerId(), loginResult.userId(), loginResult.userName());

        Authentication authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authoritiesOf(loginResult));

        // 기존 세션이 있으면 인증정보 저장 전에 세션 ID를 변경
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(context, request, response);

        HttpSession session = request.getSession(false);

        if (session == null) {
            throw new IllegalStateException("로그인 세션이 생성되지 않았습니다.");
        }

        // 역할이 바뀌어도 세션은 같은 객체로 남는다 — ChangeSessionIdAuthenticationStrategy 가 ID만 바꾸기
        // 때문이다. 그래서 역할마다 조건 없이 덮어쓴다. 덮어쓰지 않으면 관리자 세션에서 로그아웃 없이
        // 고객으로 다시 로그인했을 때 관리자 만료시간이 그대로 남는다.
        session.setMaxInactiveInterval((int) sessionTimeout.toSeconds());

        return OffsetDateTime.now(clock).plusSeconds(sessionTimeout.toSeconds());
    }

    // 관리자만 전용 설정을 쓰고 고객은 전역 세션 설정을 그대로 쓴다. 고객 값을 따로 들고 있으면
    // server.servlet.session.timeout 과 정본이 둘로 갈려 한쪽만 고쳐도 모르게 된다.
    private Duration sessionTimeoutOf(CustomerRole role) {
        if (role == CustomerRole.ADMIN) {
            return adminSessionProperties.sessionTimeout();
        }

        return serverProperties.getServlet().getSession().getTimeout();
    }

    /**
     * 관리자 권한을 로그인 때 한 번 읽어 세션 authority 로 싣는다. 인가는 SecurityConfig 의
     * {@code hasAuthority} 가 이것만 보므로 요청마다 DB 를 읽지 않는다.
     *
     * <p>대신 권한을 거둬도 이미 로그인한 세션에서는 유지된다. 즉시 끊어야 하면 권한 행만 지우지 말고
     * SessionRegistry 로 그 관리자의 세션을 만료시킨다.
     */
    private List<GrantedAuthority> authoritiesOf(LoginResult loginResult) {
        String roleAuthority = "ROLE_" + loginResult.role().name();

        if (loginResult.role() != CustomerRole.ADMIN) {
            return AuthorityUtils.createAuthorityList(roleAuthority);
        }

        Stream<String> permissionAuthorities = adminPermissionFacade.findPermissions(loginResult.customerId()).stream()
                .map(AdminPermission::name);

        return AuthorityUtils.createAuthorityList(
                Stream.concat(Stream.of(roleAuthority), permissionAuthorities).toList());
    }
}
