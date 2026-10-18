package com.shinhan.corebank.auth.adapter.in.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

public class RoleSessionTimeoutStrategy implements SessionAuthenticationStrategy {

    // 관리자 인증은 30분, 그 외 인증은 고객 기본값인 10분으로 설정한다.
    @Override
    public void onAuthentication(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        boolean administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        request.getSession(true).setMaxInactiveInterval(administrator ? 1800 : 600);
    }
}
