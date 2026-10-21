package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.adapter.in.security.CsrfTokenCookieIssuer;
import com.shinhan.corebank.auth.adapter.in.security.SessionLoginManager;
import com.shinhan.corebank.auth.application.port.in.LoginCommand;
import com.shinhan.corebank.auth.application.port.in.LoginResult;
import com.shinhan.corebank.auth.application.port.in.LoginUseCase;
import com.shinhan.corebank.customer.api.CustomerRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 고객·관리자 로그인이 공유하는 흐름이다 — 요청 IP 를 읽고, 자격을 검증하고, 세션과 CSRF 쿠키를 발급한다.
 *
 * <p>두 경로의 차이는 요구하는 역할뿐이라 컨트롤러를 복제하지 않고 {@code requiredRole} 로 받는다.
 * 세션 만료는 {@code SessionLoginManager} 가 역할에 따라 정한다.
 */
@Component
@RequiredArgsConstructor
class LoginSessionIssuer {

    private final LoginUseCase loginUseCase;
    private final ClientIpResolver clientIpResolver;
    private final SessionLoginManager sessionLoginManager;
    private final CsrfTokenCookieIssuer csrfTokenCookieIssuer;

    LoginResponse authenticateAndIssueSession(
            LoginRequest loginRequest,
            CustomerRole requiredRole,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        String requestIp = clientIpResolver.resolve(servletRequest);

        LoginResult loginResult = loginUseCase.login(
                new LoginCommand(loginRequest.userId(), loginRequest.password(), requestIp, requiredRole));

        OffsetDateTime sessionExpiresAt =
                sessionLoginManager.establishSession(loginResult, servletRequest, servletResponse);

        csrfTokenCookieIssuer.rotate(servletRequest, servletResponse);

        return new LoginResponse(loginResult.customerId(), loginResult.userName(), sessionExpiresAt);
    }
}
