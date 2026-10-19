package com.shinhan.corebank.auth.adapter.in.security;

import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.auth.application.port.in.LoginResult;
import com.shinhan.corebank.customer.api.CustomerRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

// 로그인 성공 고객을 Spring Security 인증 세션으로 저장
@Component
@RequiredArgsConstructor
public class SessionLoginManager {

    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AdminSessionProperties adminSessionProperties;
    private final Clock clock;

    public OffsetDateTime establishSession(
            LoginResult loginResult, HttpServletRequest request, HttpServletResponse response) {
        Objects.requireNonNull(loginResult, "loginResult must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");

        AuthenticatedCustomer principal =
                new AuthenticatedCustomer(loginResult.customerId(), loginResult.userId(), loginResult.userName());

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                AuthorityUtils.createAuthorityList("ROLE_" + loginResult.role().name()));

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

        // 만료시각을 읽기 전에 덮어써야 응답의 sessionExpiresAt 도 관리자 값이 된다.
        if (loginResult.role() == CustomerRole.ADMIN) {
            session.setMaxInactiveInterval(
                    (int) adminSessionProperties.sessionTimeout().toSeconds());
        }

        int timeoutSeconds = session.getMaxInactiveInterval();

        if (timeoutSeconds <= 0) {
            throw new IllegalStateException("세션 만료시간은 1초 이상이어야 합니다.");
        }

        return OffsetDateTime.now(clock).plusSeconds(timeoutSeconds);
    }
}
